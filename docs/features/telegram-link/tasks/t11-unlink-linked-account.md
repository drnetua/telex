---
id: T11
title: "Unlink an account: bounded sign-out, one-transaction delete with AccountUnlinked, then destroy the session"
layer: "app"
deps: ["T5", "T7"]
blocks: ["T14", "T24"]
acs: ["AC-111", "AC-112", "AC-113"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/LinkedAccounts.kt", "backend/app/src/main/kotlin/telex/messaging/internal/account/", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkIT.kt"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "done"
---

# T11 — Unlink an account: bounded sign-out, one-transaction delete with AccountUnlinked, then destroy the session

## Place in the sequence

- **Blocked by:** T5 — Manage Telegram session directories: per-session dir, destroy with one-minute retry, startup orphan sweep, T7 — Build the LinkedAccount aggregate: rules, states, masked phone, repository, events and the account limit · **Blocks:** T14 — Expose list and unlink of my Linked Accounts, and back getMe.linkedAccountCount with messaging, T24 — Prove nothing is left behind or leaked: unlink dump, restart delivery, registry and log scans · **Wave:** 3 — needs the repository (T7) and directory destroy (T5); parallel to the wizard chain.
- **Lane:** shares `telex/messaging/internal/account/` with T7; shares `telex/messaging/internal/account/` with T10 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to unlink a Linked Account so that teleX signs out of it and deletes everything it stored for it
> **So that** no copy of my Telegram access stays in teleX
>
> — `spec.md §4, US-03, verbatim` · full text: [spec.md](../spec.md)

This task makes an unlink total: Telegram is told to sign teleX out, and nothing teleX stored for the account survives.

## Inlined context

> Msg->>Tg: log out session, wait up to 10 s
> alt Telegram confirms the sign-out → confirmed / else unreachable, timed out or session already lost → not confirmed
> Msg->>Msg: one transaction deletes the account, its sealed key and its chat list, records AccountUnlinked
> Msg->>Tg: close and destroy the session directory
> Msg-->>SPA: unlinked, with the check-active-sessions warning if not confirmed
> AccountUnlinked stays in the event registry until every listener has run, also across a restart
>
> — `sad.md §6, Critical flow 2, abridged` · full text: [sad.md](../sad.md)

> The account row, its sealed session key and every chat-list row live in one module, so an unlink deletes them in one database transaction. The same transaction records `AccountUnlinked` in the event publication registry. "Leaves nothing" then rests on a commit, not on event delivery.
>
> — `adr/0002 §Decision outcome, abridged` · full text: [0002-own-linked-accounts-and-their-chat-list-in-messaging-behind-a-session-only-telegram-acl.md](../adr/0002-own-linked-accounts-and-their-chat-list-in-messaging-behind-a-session-only-telegram-acl.md)

> **Hard rule:** Unlink leaves nothing: 0 stored items that identify the Telegram account (session, Telegram account id, phone number, name, chat list) for an unlinked account, always. Records that carry only teleX's internal id of the Linked Account and no Telegram data (events, metrics) are kept.
>
> — `spec.md §6, Unlink leaves nothing, abridged` · full text: [spec.md](../spec.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Table | Change |
|---|---|
| `linked_account` | `DELETE … WHERE id = ? AND owner_id = ?` (sealed key goes with the row) |
| `channel` | deleted by `ON DELETE CASCADE` in the same transaction |
| `event_publication` | `AccountUnlinked(ownerId, linkedAccountId)` recorded in the same transaction |

— `data-model.md §linked_account Unlink, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — module API behind (T14): `unlinkMyLinkedAccount` → `{signOutConfirmed: boolean}`; not among the caller's accounts → `404 not-found`.

— `contracts/openapi.yaml, operationId unlinkMyLinkedAccount, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-111 — happy

> **Given** an Owner with a connected Linked Account
> **When** they choose to unlink it and confirm in a dialog that names what will happen
> **Then** teleX signs out of that Telegram account so the teleX device disappears from the account's active sessions in Telegram, deletes the account's session and every chat-list entry it synced, and removes the account from the list; if it was the Owner's last Linked Account, the Inbox shows the "Connect Telegram" step again
>
> — `spec.md §5, AC-111, verbatim` · full text: [spec.md](../spec.md)

### AC-112 — cross-context

> **Given** an Owner who unlinks a Linked Account
> **When** the unlink completes, and also after teleX restarts, even if it stopped right after the unlink
> **Then** the account no longer appears anywhere in teleX that lists or offers the Owner's Linked Accounts and can't be chosen for anything; linking the same Telegram account again creates a new Linked Account with nothing attached; from E09 and E20 on, the same unlink pauses its agents and cancels their scheduled runs (AC-05, moved)
>
> — `spec.md §5, AC-112, verbatim` · full text: [spec.md](../spec.md)

### AC-113 — error

> **Given** an Owner whose Linked Account has lost its session, or whose Telegram can't be reached at that moment
> **When** they unlink it
> **Then** teleX still deletes everything it stored for the account and removes it from the list, and tells the Owner that Telegram couldn't confirm the sign-out, so they should check active sessions in the Telegram app
>
> — `spec.md §5, AC-113, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `LinkedAccounts.unlink(owner, id)`: find mine (else not-found), `logOut(session, 10 s)` when the account has a session (Session lost / reset → not confirmed)
- [ ] `@Transactional` delete + publish `AccountUnlinked`; discard an open attempt whose target is this account
- [ ] After commit: `close` + `destroy` the session (T5 retries failures); metric `telex.unlink{signout=confirmed|unconfirmed}`
- [ ] `UnlinkIT` (fake): confirmed, unreachable and Session-lost cases; no `linked_account`/`channel` row and no fake session remain; `AccountUnlinked` published with ids only

## Edge cases

| Case | Behaviour |
|---|---|
| Telegram unreachable or timed out | Still deletes everything; `signOutConfirmed: false` (AC-113) |
| Already Session lost | No log-out possible; deletes; `signOutConfirmed: false` |
| Another Owner's id / already unlinked | `not-found`, nothing changes (AC-03) |
| Re-link the same Telegram account later | New `LinkedAccountId`, nothing attached (AC-112) |

## Definition of Done

- [ ] `UnlinkIT` passes for all edge cases
- [ ] the delete and the publication commit atomically (one transaction)
- [ ] detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
