---
id: T4
title: "Build the Owner and Sign-in Session core: start, resolve with the 30/90-day rules, end by key, and the SignInSessionStarted event"
layer: "infra"
deps: ["T1", "T2"]
blocks: ["T6", "T7", "T8", "T10"]
acs: ["AC-96", "AC-104"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/SignInSessions.kt", "backend/app/src/main/kotlin/telex/identity/SignInSessionStarted.kt", "backend/app/src/main/kotlin/telex/identity/internal/owner/", "backend/app/src/main/kotlin/telex/identity/internal/session/", "backend/app/src/integrationTest/kotlin/telex/identity/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T4 — Build the Owner and Sign-in Session core: start, resolve with the 30/90-day rules, end by key, and the SignInSessionStarted event

## Place in the sequence

- **Blocked by:** T1 — Promote the four staged identity migrations into the live Flyway tree, T2 — Add identity domain primitives: typed ids, Clock, email canonicalisation, secrets, device naming and the public URL · **Blocks:** T6 — Redeem a Sign-in Link or Sign-in Code atomically and start the Sign-in Session, T7 — Send the "New sign-in to teleX" email from the SignInSessionStarted event after commit, T8 — Add the Spring Security filter chain with the session cookie, CSRF and no-store, and switch problem codes to kebab-case, T10 — List and end my Sign-in Sessions and answer "who am I", Owner-scoped end to end · **Wave:** 2 — needs the tables (T1) and the typed ids, secrets and device mapper (T2).
- **Lane:** shares `identity/SignInSessions.kt` with T10 (T10 depends on this task, so they serialize).

## Why (user story)

> **As an** Owner
> **I want** to see my active Sign-in Sessions and Passkeys, end any session, and sign out
> **So that** a lost device or a stolen session stops working when I say so
>
> — `spec.md §4, US-46, verbatim` · full text: [spec.md](../spec.md)

This task is the one session mechanism every sign-in method ends in, and the server-side clock that ends sessions nobody uses.

## Inlined context

> **Spring Security is the one gate, and every sign-in method ends in one session mechanism.** […] The Sign-in Link, the Sign-in Code and the Passkey are three ways to prove identity, and each ends in `identity`'s `SignInSessions.start(...)`. That call writes an identity-owned session row behind an opaque, hashed cookie, ends any session the browser already holds, and publishes `SignInSessionStarted`. One mechanism gives one place to enforce the 30/90-day rules, revocation and the new-sign-in email (quality goal 1).
>
> — `sad.md §4, strategic choice 1, abridged` · full text: [sad.md](../sad.md)

> - `started_at` gives the 90-day cap;
> - `last_activity_at`, updated only by user-initiated requests (ADR-0005), gives the 30-day idle rule;
> - `ended_at` covers revocation and sign-out;
> - `user_agent_label`, `device_type` and `time_zone` feed the list (AC-93) and the new-sign-in email (AC-98).
>
> The key is stored only as a hash.
>
> — `adr/0001 §Decision outcome, abridged` · full text: [adr/0001](../adr/0001-keep-sign-in-sessions-in-an-identity-owned-table-behind-an-opaque-cookie.md)

> **Session activity:** Unmarked requests bump `last_activity_at` at most once a minute; `X-Telex-Background: 1` requests don't (AC-96)
>
> — `sad.md §8, Session activity, verbatim` · full text: [sad.md](../sad.md) · decision: [adr/0005](../adr/0005-count-session-activity-only-from-requests-the-spa-does-not-mark-as-background.md)

> Critical flow 3: resolve session by key hash → *no cookie or unknown key* → `unauthenticated`; *ended, idle 30 days or started 90 days ago* → mark ended if it just expired → `session-ended`; *live* → opt (user-initiated and last bump over a minute ago) update last activity → perform the action scoped to this Owner.
>
> — `sad.md §6, Critical flow 3, abridged` · full text: [sad.md](../sad.md)

> Event `telex.identity.SignInSessionStarted` — required payload fields: `ownerId`, `sessionId`, `createdAccount`. No email address, no secrets (sad §8 Events). Producer: `SignInSessions.start(...)`, in the same transaction that inserts the session.
>
> — `contracts/events.md §Event identity.sign-in-session-started.v1, abridged` · full text: [events.md](../contracts/events.md)

> Time zone: the signing-in browser's IANA time zone […] Missing or not a known zone → `UTC`; never an error.
>
> — `contracts/openapi.yaml, parameter TimeZone, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

Uses from T2: `OwnerId`, `SignInSessionId`, the 256-bit token + SHA-256 helpers, the device mapper, the `Clock` bean.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Table.column | Use | Change |
|---|---|---|
| `owner.id`, `email`, `canonical_email`, `created_at` | find by canonical (`owner_canonical_email_uq`); create-if-absent via `INSERT … ON CONFLICT (canonical_email) DO NOTHING` + re-read | read/write |
| `sign_in_session.*` | insert on start; resolve by `key_hash` (`sign_in_session_key_hash_uq`); `ended_at` on replace/expiry/sign-out; `last_activity_at` bump | read/write |

**Live** = `ended_at IS NULL AND last_activity_at > :now - 30 days AND started_at > :now - 90 days`.

— `data-model.md §Aggregate: Owner + §Aggregate: Sign-in Session, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no HTTP surface (controllers are T9/T10). Public Kotlin API at the `identity` root:

- `SignInSessions.start(ownerId, heldKey: String?, userAgent: String?, timeZone: String?, createdAccount: Boolean): StartedSession(sessionId, key)` — one transaction: end the held session (any Owner), insert, publish `SignInSessionStarted(ownerId, sessionId, createdAccount)`.
- `SignInSessions.resolve(key: String, background: Boolean): SessionResolution` — `Live(ownerId, sessionId)` · `Ended` · `Unknown`.
- `SignInSessions.endByKey(key: String?)` — sign-out; no-op for null/unknown/ended.
- Internal (for T6): `Owners` repository `findOrCreate(emailAsTyped, canonical, now): Pair<OwnerId, created: Boolean>`.

## Acceptance criteria

### AC-96 — domain invariant

> **Given** a Sign-in Session that has been idle for 30 days (activity means a page the Owner opens or an action they take; background refreshes of an open tab don't count), or that started 90 days ago no matter how active it is
> **When** the Owner next opens teleX in that browser
> **Then** they see the "Session ended" page with a "Sign in again" action
>
> — `spec.md §5, AC-96, verbatim` · full text: [spec.md](../spec.md)

### AC-104 — domain invariant

> **Given** a browser that is already signed in, as the same Owner or as another one
> **When** a Sign-in Link is confirmed or a Sign-in Code is typed in that browser
> **Then** the existing Sign-in Session in that browser ends and a new one starts for the Owner the email was sent to, with the "New sign-in to teleX" email per AC-98
>
> — `spec.md §5, AC-104, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Owner aggregate + Spring Data JDBC repository with the `ON CONFLICT` find-or-create in `identity/internal/owner/`.
- [ ] SignInSession aggregate + repository in `identity/internal/session/` (live predicate, end-held, mark-ended, bump-if-older-than-1-minute as conditional `UPDATE`s).
- [ ] `SignInSessions` service + `SignInSessionStarted` event at the identity root; publish with `ApplicationEventPublisher` inside `@Transactional`.
- [ ] Fixtures `anOwner(...)`, `aSession(...)` (data-model §Test fixtures) under `backend/app/src/integrationTest/kotlin/telex/identity/`.
- [ ] `SignInSessionsIT` with a mutable fixed `Clock`.

## Edge cases

| Case | Behaviour |
|---|---|
| Held key belongs to another Owner | that session ends anyway; new one starts for this Owner (AC-104) |
| Held key unknown or already ended | ignored; new session starts |
| Background-only requests for 31 days | `resolve` → `Ended`, row marked `ended_at` (QG-1c) |
| A user request on day 29, then quiet | still live on day 30 (bump reset the idle clock) |
| Daily user requests up to day 90 | `Ended` at 90 days from `started_at` regardless of activity |
| Two user requests within one minute | one `last_activity_at` write |
| Two concurrent first sign-ins for one canonical address | one `owner` row; both callers get the same `OwnerId` |
| Unknown time zone `Mars/Base` or missing | stored as `UTC` |

## Definition of Done

- [ ] `SignInSessionsIT` covers every edge case above with a fixed `Clock` and passes under `./gradlew :backend:app:integrationTest`.
- [ ] The event is asserted with Modulith test support (`PublishedEvents` / `Scenario`) and carries no email or key.
- [ ] A search of `sign_in_session` and `event_publication` for the raw key finds nothing (QG-1d, session part).
- [ ] every Hard Rule inlined above still holds.
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green.
