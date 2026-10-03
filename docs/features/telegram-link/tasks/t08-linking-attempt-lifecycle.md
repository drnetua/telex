---
id: T8
title: "Start, resume, cancel and expire the in-memory linking attempt (one per Owner)"
layer: "app"
deps: ["T3", "T4", "T7"]
blocks: ["T9"]
acs: ["AC-109", "AC-110", "AC-119", "AC-114"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/Linking.kt", "backend/app/src/main/kotlin/telex/messaging/internal/attempt/", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingAttemptIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T8 — Start, resume, cancel and expire the in-memory linking attempt (one per Owner)

## Place in the sequence

- **Blocked by:** T3 — Add OwnerKeys envelope encryption with the master-key check and reset, and SignInSessions.isLive, T4 — Define the TelegramSessions port, its in-process events and the fake Telegram adapter, T7 — Build the LinkedAccount aggregate: rules, states, masked phone, repository, events and the account limit · **Blocks:** T9 — Run the phone, code, resend and password steps with Telegram's refusals and waits · **Wave:** 3 — needs `isLive` + `OwnerKeys.ready()` (T3), the port (T4) and the account count (T7).
- **Lane:** shares `telex/messaging/Linking.kt` with T9; shares `telex/messaging/Linking.kt` with T10 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to link my Telegram account to teleX with my phone number, the code Telegram sends me and my password if I have one
> **So that** teleX can work with my chats on my behalf
>
> — `spec.md §4, US-02, verbatim` · full text: [spec.md](../spec.md)

This task gives the wizard its server-side memory: the step lives on the server, so a reload or another device continues it, and an abandoned attempt leaves nothing behind.

## Inlined context

> Linking attempt: At most one per Owner, held in memory by `messaging`. It holds the open Telegram session, the step, where it started (SCR-10 or SCR-60), the target account for "Sign in again", the Sign-in Session that last stepped it and the last-step time. It is discarded on cancel, after 15 min without a step (a one-minute sweep), when the Sign-in Session that last stepped it is no longer live (checked through `identity`'s `SignInSessions.isLive` on every step and in the one-minute sweep, AC-110), or on restart. Discarding always closes and destroys its Telegram session.
>
> — `sad.md §8, Linking attempt, abridged` · full text: [sad.md](../sad.md)

> Flow 4: Telegram app credentials missing, or no master key on a fresh installation → refusal linking not set up. / this Owner already has an open attempt (reload, second tab or another device) → attempt at its current step. / no open attempt → count this Owner's Linked Accounts, Session lost included → limit reached → refusal with the limit; below the limit → open a new Telegram session with a fresh database key, hold the attempt in memory with its step, start point and Sign-in Session → attempt at the phone step. The attempt and its key are never persisted, a restart discards them.
>
> — `sad.md §6, Flow 4, abridged` · full text: [sad.md](../sad.md)

> `messaging` generates the TDLib key when a linking attempt starts and keeps it only in memory until the account is created.
>
> — `adr/0003 §Decision outcome, abridged` · full text: [0003-seal-each-tdlib-database-key-with-a-stored-per-owner-key-under-an-installation-master-key.md](../adr/0003-seal-each-tdlib-database-key-with-a-stored-per-owner-key-under-an-installation-master-key.md)

> **Hard rule:** The Operator sees no Owner's Telegram data: metrics carry no phone numbers, names or Telegram ids in tags (`telex.linking.attempts{outcome=…}`).
>
> — `sad.md §7, Monitoring, abridged` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes. (Reads `linked_account` via T7's `countMine`; the attempt is never stored — `data-model.md §Not stored, Linking attempt`.)

## API contract

Internal — the module API behind (T15): `getMyLinkingAttempt`, `startMyLinkingAttempt` (`origin`, `targetLinkedAccountId?` → `200` resumed / `201` new; refusals `telegram-linking-not-set-up` 503, `linked-account-limit-reached` 409 + `limit`, `not-found` 404 and `telegram-account-already-linked` 409 for a target that isn't the caller's / isn't Session lost), `cancelMyLinkingAttempt` (no-op when none), `linking-attempt-not-found` 404 when the attempt is gone.

— `contracts/openapi.yaml, operationIds getMyLinkingAttempt / startMyLinkingAttempt / cancelMyLinkingAttempt, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-109 — domain invariant

> **Given** an Owner who started the linking wizard
> **When** they cancel it, or take no step in it for 15 minutes
> **Then** the attempt is discarded, no teleX device from it remains in the account's active sessions in Telegram, and starting again begins with the phone number; until then an Owner has at most one open attempt, and reloading the page or opening the wizard in another tab or device continues it at the step where it stopped
>
> — `spec.md §5, AC-109, verbatim` · full text: [spec.md](../spec.md)

### AC-110 — cross-context

> **Given** an Owner with a connected Linked Account
> **When** they sign out of teleX, or their Sign-in Session ends or is revoked
> **Then** the Linked Account stays connected and keeps syncing, and the next time they sign in it is still there; and an Owner whose Sign-in Session ends in the middle of the linking wizard must sign in again and start the wizard over, with no session kept from the unfinished attempt
>
> — `spec.md §5, AC-110, verbatim` · full text: [spec.md](../spec.md)

### AC-119 — error

> **Given** an installation whose Operator hasn't given it Telegram app credentials
> **When** an Owner chooses "Connect Telegram" or "Add account"
> **Then** teleX says that Telegram linking isn't set up on this installation yet and that the Operator has to finish the setup, and doesn't start the wizard
>
> — `spec.md §5, AC-119, verbatim` · full text: [spec.md](../spec.md)

### AC-114 — happy

> **Given** an Owner with one Linked Account and an installation limit of more than one
> **When** they add another account from the Accounts page and complete the wizard
> **Then** both accounts appear in the Accounts list, each with its Telegram name, masked phone number and state, and each syncs its own chat list
>
> — `spec.md §5, AC-114, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `internal/attempt/LinkingAttempt` (session id, 32-byte `SecureRandom` key, step, origin, target, last Sign-in Session, last-step time) + `LinkingAttempts` store keyed by `OwnerId` (`ConcurrentHashMap`), steps serialized per Owner
- [ ] `Linking.start(owner, signInSession, origin, target?)`: not `configured()` or not `OwnerKeys.ready()` → not set up; open attempt with live session → resume; open attempt with dead session → discard, then start fresh; target → must be mine (`not-found`) and Session lost (`already-linked`), no limit check; else limit check → `open(key)`
- [ ] `Linking.get(owner, signInSession)` (discard if its session isn't live) and `Linking.cancel(owner)`
- [ ] One-minute `@Scheduled` sweep with the injected `Clock`: 15 min without a step or `isLive` false → discard; discard = remove + `close` + `destroy` the session
- [ ] Domain problems for each refusal (extend `telex.shared.DomainProblem`), metric `telex.linking.attempts{outcome=cancelled|expired}`
- [ ] `LinkingAttemptIT` (`@ApplicationModuleTest`, fake adapter, fixed clock) for every edge below

## Edge cases

| Case | Behaviour |
|---|---|
| Second tab / device calls start | Same attempt returned at its step, body ignored (AC-109) |
| 15 min without a step | Swept; the fake has no session left; next start begins at the phone step |
| Sign-in Session ended mid-wizard | Attempt discarded on the next start/get/sweep (AC-110) |
| Cancel when nothing is open | No-op |
| No credentials, or no master key on a fresh installation | `telegram-linking-not-set-up`, nothing opened (AC-119) |

## Definition of Done

- [ ] `LinkingAttemptIT` passes for every edge case
- [ ] no attempt or key is persisted (no new table, nothing in `event_publication`)
- [ ] `ModularityTest` green; detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
