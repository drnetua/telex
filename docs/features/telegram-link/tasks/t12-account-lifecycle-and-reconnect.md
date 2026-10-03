---
id: T12
title: "Reconnect accounts on boot and follow Telegram's session state (Connected, Reconnecting, Session lost)"
layer: "app"
deps: ["T3", "T5", "T7"]
blocks: ["T24"]
acs: ["AC-36", "AC-118", "AC-117", "AC-122"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/internal/lifecycle/", "backend/app/src/integrationTest/kotlin/telex/messaging/LifecycleIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T12 — Reconnect accounts on boot and follow Telegram's session state (Connected, Reconnecting, Session lost)

## Place in the sequence

- **Blocked by:** T3 — Add OwnerKeys envelope encryption with the master-key check and reset, and SignInSessions.isLive, T5 — Manage Telegram session directories: per-session dir, destroy with one-minute retry, startup orphan sweep, T7 — Build the LinkedAccount aggregate: rules, states, masked phone, repository, events and the account limit · **Blocks:** T24 — Prove nothing is left behind or leaked: unlink dump, restart delivery, registry and log scans · **Wave:** 3 — needs the reset flag (T3), the sweep (T5) and the repository (T7).
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** my Linked Accounts to come back on their own after teleX restarts
> **So that** I never have to type a Telegram code again just because the server restarted
>
> — `spec.md §4, US-52, verbatim` · full text: [spec.md](../spec.md)

This task keeps accounts connected by themselves across restarts and outages, and shows Session lost only when Telegram confirms it.

## Inlined context

> Critical flow 3: startup sweep deletes session directories no Linked Account references → load every Linked Account that is not Session lost → each account, in parallel: open its sealed TDLib key, open existing session with the key → session still valid: state Ready → Connected, resume sync where it stopped / Telegram unreachable: state Connecting → Reconnecting, TDLib keeps retrying / session ended while teleX was stopped: state Closed → Session lost, keep the account and its chat list → LinkedAccountStateChanged.
>
> — `sad.md §6, Critical flow 3, abridged` · full text: [sad.md](../sad.md)

> Flow 9: map the Telegram session to its Linked Account, drop the change if its sequence isn't newer → connection lost: set the account Reconnecting […] authorization ready again → Connected / authorization closed: set the account Session lost, keep its chat list and everything attached; close the Telegram client, keep its directory until Sign in again or unlink → record LinkedAccountStateChanged.
> Published only on a real change of the stored state, so a TDLib reconnect storm doesn't flood the registry.
>
> — `sad.md §6 Flow 9 + events.md, abridged` · full text: [sad.md](../sad.md)

> Recovery from a truly lost key is explicit: start once with `TELEX_MASTER_KEY_RESET=true` and the new key. That deletes every Owner key, sealed TDLib key and session directory, puts every Linked Account in "Session lost" (kept, with "Sign in again"). Master-key reset: `UPDATE linked_account SET state = 'session_lost', telegram_session_id = NULL, tdlib_key_sealed = NULL`.
>
> — `sad.md §7 + data-model.md §linked_account, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** A Telegram outage never shows "Session lost"; "Session lost" shows ≤ 5 min after the session is ended in Telegram. Readiness doesn't wait for the reconnects.
>
> — `spec.md §6 Lost session detection + sad.md §7 Boot order, abridged` · full text: [spec.md](../spec.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Column | Change |
|---|---|
| `linked_account.state` | updated on a real change (by `telegram_session_id`) |
| `linked_account.telegram_session_id`, `tdlib_key_sealed` | set NULL with `state='session_lost'` on master-key reset only |
| `event_publication` | `LinkedAccountStateChanged(ownerId, linkedAccountId, state)` |

— `data-model.md §linked_account, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no API surface.

## Acceptance criteria

### AC-36 — happy

> **Given** an Owner with 2 connected Linked Accounts
> **When** teleX restarts
> **Then** both accounts are connected again without the Owner typing any code
>
> — `spec.md §5, AC-36, verbatim` · full text: [spec.md](../spec.md)

### AC-118 — error

> **Given** an Owner with 2 Linked Accounts, one of whose sessions Telegram ended while teleX was stopped
> **When** teleX restarts
> **Then** that account shows "Session lost" and the other one is connected again as usual
>
> — `spec.md §5, AC-118, verbatim` · full text: [spec.md](../spec.md)

### AC-117 — error

> **Given** an Owner with a connected Linked Account
> **When** the teleX session is ended from the Telegram app or by Telegram itself
> **Then** within 5 minutes the account shows "Session lost" with a "Sign in again" action; signing in again with the same Telegram account brings back the same Linked Account with everything attached to it, while signing in with a different Telegram account at that point is refused, that sign-in is ended so no teleX device remains in Telegram, and the Owner is told to link it as a new account (or, if it belongs to another Owner, sees the rule from AC-04)
>
> — `spec.md §5, AC-117, verbatim` · full text: [spec.md](../spec.md)

### AC-122 — error

> **Given** an Owner with a connected Linked Account
> **When** teleX temporarily can't reach Telegram, or Telegram confirms that the account's session has ended
> **Then** while Telegram is unreachable the account shows "Reconnecting" on the Accounts page and teleX reconnects on its own without asking the Owner for anything; only when Telegram confirms the session has ended does the account show "Session lost", and then every signed-in screen also shows a Status Banner saying the account is disconnected, with a "Sign in again" action, until the Owner signs in again or unlinks it
>
> — `spec.md §5, AC-122, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `internal/lifecycle/BootReconnect` on `ApplicationReadyEvent`: if `OwnerKeys.resetPerformedAtStartup` → reset every account + `sweepOrphans(∅)`; else `sweepOrphans(all referenced session ids)`; then reopen each non-lost account on a virtual thread (`open` sealed key → `reopen`)
- [ ] `internal/lifecycle/SessionStateListener` (`@EventListener` on `TelegramSessionStateChanged`): unknown session → drop; per-session last sequence in memory, stale → drop; map Ready/Connecting/Closed; on Closed also `close` the client (keep the dir)
- [ ] Update state only when it changes; publish `LinkedAccountStateChanged`; metrics `telex.telegram.sessions.active{state}`, `telex.linked_accounts.reconnect.duration`
- [ ] `LifecycleIT` (fake, fixed clock): restart with 2 accounts (AC-36); one ended while stopped (AC-118); termination → Session lost within the window; connectivity drop → Reconnecting only; reset → all Session lost

## Edge cases

| Case | Behaviour |
|---|---|
| Connectivity drop then return | Reconnecting → Connected; never Session lost; no banner (AC-122) |
| Out-of-order state callbacks | Older sequence dropped |
| Event for an unlinked/replaced session | Dropped (ADR-0002) |
| Master-key reset | Every account Session lost with "Sign in again" (sad §1 ¶4 override) |

## Definition of Done

- [ ] `LifecycleIT` passes for every edge; reconnect of 2 accounts ≤ 60 s after ready on the fake
- [ ] app readiness doesn't block on reconnects
- [ ] detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
