---
id: T10
title: "List and end my Sign-in Sessions and answer \"who am I\", Owner-scoped end to end"
layer: "ports"
deps: ["T4", "T8"]
blocks: ["T20"]
acs: ["AC-93", "AC-94", "AC-97", "AC-100"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/SignInSessions.kt", "backend/app/src/main/kotlin/telex/identity/Owners.kt", "backend/app/src/main/kotlin/telex/identity/internal/session/", "backend/app/src/main/kotlin/telex/web/api/SessionsController.kt", "backend/app/src/main/kotlin/telex/web/api/MeController.kt", "backend/app/src/integrationTest/kotlin/telex/web/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T10 — List and end my Sign-in Sessions and answer "who am I", Owner-scoped end to end

## Place in the sequence

- **Blocked by:** T4 — Build the Owner and Sign-in Session core: start, resolve with the 30/90-day rules, end by key, and the SignInSessionStarted event, T8 — Add the Spring Security filter chain with the session cookie, CSRF and no-store, and switch problem codes to kebab-case · **Blocks:** T20 — Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator · **Wave:** 4 — needs the session core (T4) and the security context (T8).
- **Lane:** shares `identity/SignInSessions.kt` with T4 (serialized after it) and `web/api/` with T9/T12.

## Why (user story)

> **As an** Owner
> **I want** to see my active Sign-in Sessions and Passkeys, end any session, and sign out
> **So that** a lost device or a stolen session stops working when I say so
>
> — `spec.md §4, US-46, verbatim` · full text: [spec.md](../spec.md)

This task lets an Owner see every place they are signed in and cut any of them off, and gives the SPA its "who am I" query.

## Inlined context

> **Authorization:** Owner-scoped by construction: every `identity` query for sessions, passkeys or profile takes the `OwnerId` from the security context and filters on it. […] Another Owner's record is indistinguishable from a missing one, with the same `not-found` problem (AC-97). No roles in E01 (the Operator role is E26)
>
> — `sad.md §8, Authorization, abridged` · full text: [sad.md](../sad.md)

> Flow US-46 manage Sign-in Sessions: list my sessions → read the live sessions filtered by owner id → each session's browser, device type and last activity, with the current one flagged → *ends the phone's session* → mark it ended only if it belongs to this Owner → *not among this Owner's sessions* → not found, exactly as for a session that never existed (AC-97) → *ended* → the phone's next action gets session-ended and lands on SCR-92 (Critical flow 3). *Signs out of all other sessions* → mark ended every live session of this Owner except the current one (AC-94).
>
> — `sad.md §6, Flow US-46 manage Sign-in Sessions, abridged` · full text: [sad.md](../sad.md)

> Flow US-01 landing after sign-in: who am I → read the Owner by owner id → no Linked Account store exists before E02, so the Linked Account count is zero → address and a Linked Account count of zero.
>
> — `sad.md §6, Flow US-01 landing after sign-in, abridged` · full text: [sad.md](../sad.md)

Uses: T8's security context (principal carries `OwnerId` + current `SignInSessionId`), T4's session repository and live predicate. `Owners.me(ownerId)` is new public API at the identity root (sad §5: `Owners` "who am I").

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Column | Use | Change |
|---|---|---|
| `sign_in_session.owner_id` (`sign_in_session_owner_id_idx`) | list / end / end-others filtered by the caller's `OwnerId` | read |
| `sign_in_session.ended_at` | set by end-one and end-others (`… WHERE id = :id AND owner_id = :owner AND <live>`) | write |
| `owner.email`, `owner.id` | "who am I" | read |

— `data-model.md §Aggregate: Sign-in Session, access patterns, abridged` · full text: [data-model.md](../data-model.md)

## API contract

| operationId | Success | Errors |
|---|---|---|
| `getMe` `GET /api/v1/me` | `200 {ownerId, email, linkedAccountCount: 0}` — `email` = `owner.email` (as created) | `401` NotSignedIn |
| `listMySessions` `GET /api/v1/sessions` | `200 {items: [{id, userAgentLabel, deviceType, startedAt, lastActivityAt, current}]}` newest first, whole list | `401` |
| `endMySession` `DELETE /api/v1/sessions/{sessionId}` | `204`; ending the current session is allowed (cookie not cleared) | `401`, `403`, `404 not-found` |
| `endMyOtherSessions` `POST /api/v1/sessions/end-others` | `204` | `401`, `403` |

`GET`s accept `X-Telex-Background: 1` (not counted as activity).

— `contracts/openapi.yaml, operationIds getMe / listMySessions / endMySession / endMyOtherSessions, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-93 — happy

> **Given** an Owner signed in on a laptop and a phone
> **When** they open Profile and security on the laptop and end the phone's session
> **Then** the list shows each session's browser, device type and last activity, with the current one marked "This device", and the phone's next action lands on the "Session ended" page
>
> — `spec.md §5, AC-93, verbatim` · full text: [spec.md](../spec.md)

### AC-94 — happy

> **Given** an Owner signed in on several devices
> **When** they choose "Sign out of all other sessions"
> **Then** every session except the current one ends, and the list shows only "This device"
>
> — `spec.md §5, AC-94, verbatim` · full text: [spec.md](../spec.md)

### AC-97 — authorization

> **Given** two Owners on the same installation
> **When** one Owner tries to see, end or remove the other Owner's Sign-in Session or Passkey
> **Then** nothing changes, and it looks as if that session or passkey doesn't exist; each Owner only ever sees their own
>
> — `spec.md §5, AC-97, verbatim` · full text: [spec.md](../spec.md)

### AC-100 — cross-context

> **Given** a signed-in Owner who has no Linked Account
> **When** they open the Inbox
> **Then** they see the empty Inbox with the single step "Connect Telegram"; the step stays until the Owner has at least one Linked Account
>
> — `spec.md §5, AC-100, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `SignInSessions.listMine(ownerId, currentId)`, `endMine(ownerId, sessionId): Boolean`, `endMyOthers(ownerId, currentId)` (conditional `UPDATE`s, live only).
- [ ] `Owners.me(ownerId): Me(ownerId, email, linkedAccountCount = 0)` at the identity root.
- [ ] `web/api/MeController.kt`, `web/api/SessionsController.kt`; `false` from `endMine` → `404 not-found` `DomainProblem`.
- [ ] `SessionsApiIT`: two Owners, laptop + phone sessions, fixed `Clock`.

## Edge cases

| Case | Behaviour |
|---|---|
| Owner A deletes Owner B's session id | `404 not-found`; B's session untouched (AC-97) |
| Delete an already-ended or random UUID | `404 not-found` — same body as above |
| Phone session ended, phone's next request | `401 session-ended` (AC-93) |
| Delete the current session | `204`; the next request from this browser is `401 session-ended` |
| End-others with only the current session | `204`, nothing changes |
| Idle-expired sessions of the Owner | not listed (live predicate) |

## Definition of Done

- [ ] `SessionsApiIT` covers every edge case and passes.
- [ ] Responses match the contract field names and `deviceType` enum.
- [ ] No query in this task can return another Owner's row (asserted with two Owners).
- [ ] every Hard Rule inlined above still holds.
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green.
