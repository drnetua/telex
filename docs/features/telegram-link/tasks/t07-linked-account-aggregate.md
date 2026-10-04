---
id: T7
title: "Build the LinkedAccount aggregate: rules, states, masked phone, repository, events and the account limit"
layer: "domain"
deps: ["T2"]
blocks: ["T8", "T11", "T12", "T13", "T14", "T16"]
acs: ["AC-04", "AC-108", "AC-115", "AC-03"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/LinkedAccountId.kt", "backend/app/src/main/kotlin/telex/messaging/ChannelId.kt", "backend/app/src/main/kotlin/telex/messaging/AccountLinked.kt", "backend/app/src/main/kotlin/telex/messaging/AccountUnlinked.kt", "backend/app/src/main/kotlin/telex/messaging/LinkedAccountStateChanged.kt", "backend/app/src/main/kotlin/telex/messaging/LinkedAccountSyncProgressed.kt", "backend/app/src/main/kotlin/telex/messaging/internal/account/", "backend/app/src/main/kotlin/telex/messaging/internal/config/", "backend/app/src/test/kotlin/telex/messaging/", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkedAccountRepositoryIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "done"
---

# T7 — Build the LinkedAccount aggregate: rules, states, masked phone, repository, events and the account limit

## Place in the sequence

- **Blocked by:** T2 — Promote the owner_key, linked_account and channel migrations into the live Flyway tree · **Blocks:** T8 — Start, resume, cancel and expire the in-memory linking attempt (one per Owner), T11 — Unlink an account: bounded sign-out, one-transaction delete with AccountUnlinked, then destroy the session, T12 — Reconnect accounts on boot and follow Telegram's session state (Connected, Reconnecting, Session lost), T13 — Sync each account's chat list into channel rows with throttled progress events, T14 — Expose list and unlink of my Linked Accounts, and back getMe.linkedAccountCount with messaging, T16 — Serve the live-update SSE stream of invalidation hints per Owner · **Wave:** 2 — needs the tables (T2); runs in parallel with T3–T5.
- **Lane:** shares `telex/messaging/internal/account/` with T10; shares `telex/messaging/internal/account/` with T11 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to link more than one Telegram account, up to the installation's limit, and see all of them in one list
> **So that** my personal and work accounts both work in teleX
>
> — `spec.md §4, US-50, verbatim` · full text: [spec.md](../spec.md)

This task encodes the Linked Account rules (one Owner per Telegram account, no duplicates, the limit) that every linking, listing and unlink path relies on.

## Inlined context

> One Owner per Telegram account: Unique index on `linked_account.telegram_user_id` across the installation. The check runs after Telegram reports who signed in, not on the phone number (AC-04, AC-108). Two Owners finishing at the same moment are settled by the index: the loser's session is logged out.
> Account limit: Checked when an attempt starts and again, inside the insert transaction, when it finishes (AC-115). Session-lost accounts count, re-sign-in takes no new place, and a lowered limit never unlinks. The final count runs under a transaction-scoped Postgres advisory lock keyed by the `OwnerId`.
> Authorization: Owner-scoped by construction. `messaging` takes the `OwnerId` from the caller and filters every Linked Account and Channel query on `owner_id`. Another Owner's account behaves as missing.
>
> — `sad.md §8, One Owner + Account limit + Authorization, abridged` · full text: [sad.md](../sad.md)

> Kotlin types at the `messaging` module root (public API): `AccountLinked(ownerId, linkedAccountId)`, `AccountUnlinked(ownerId, linkedAccountId)`, `LinkedAccountStateChanged(ownerId, linkedAccountId, state)`, `LinkedAccountSyncProgressed(ownerId, linkedAccountId)`. Registry events carry teleX ids and states only.
>
> — `contracts/events.md §Channel 1, abridged` · full text: [events.md](../contracts/events.md)

> │       ├── account/                 LinkedAccount aggregate, states, one-owner + duplicate + limit rules, repository
> │       └── config/                  max linked accounts per Owner (installation-wide, default 3)
>
> — `sad.md §5, Internal decomposition, verbatim` · full text: [sad.md](../sad.md)

> **Hard rule:** Every Owner-owned row carries `owner_id`, and every query filters on it. Another Owner's record is indistinguishable from a missing one.
>
> — `sad.md §2, Conventions, verbatim` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Column | Type | Constraints | Change |
|---|---|---|---|
| `linked_account.*` (all columns) | — | see T2 | read / write |
| `channel` | — | `channel_linked_account_telegram_chat_uq` | read-only here (`COUNT(*)` per account = `chatsSynced`) |

Access patterns this repository serves: list mine with state + chat count (`owner_id` idx); count mine for the limit (under `pg_advisory_xact_lock` keyed by the Owner id); get mine `WHERE id = ? AND owner_id = ?`; who owns Telegram user U (`telegram_user_id_uq`); account by current session id (`telegram_session_id_uq`); all not Session lost (boot); all referenced session ids (sweep).

— `data-model.md §linked_account Access patterns, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no API surface. (Public module API added: `LinkedAccounts.listMine(ownerId)`, `LinkedAccounts.countMine(ownerId)`; unlink arrives in T11.)

## Acceptance criteria

### AC-04 — domain invariant

> **Given** a Telegram account that is already a Linked Account of another Owner
> **When** an Owner completes the linking wizard for that same Telegram account
> **Then** teleX blocks the link with the rule "one Telegram account belongs to one Owner", ends the sign-in it just made so no teleX device for the second Owner remains in the account's active sessions in Telegram, and leaves the other Owner's Linked Account untouched
>
> — `spec.md §5, AC-04, verbatim` · full text: [spec.md](../spec.md)

### AC-108 — domain invariant

> **Given** an Owner whose Telegram account is already one of their Linked Accounts
> **When** they go through the linking wizard for the same Telegram account again
> **Then** teleX doesn't create a second Linked Account: if that account is connected, teleX ends the sign-in it just made, so no extra teleX device remains in the account's active sessions in Telegram, and tells them the account is already linked; if that account has lost its session, the wizard counts as "Sign in again" and brings back the same Linked Account (AC-117). A Telegram account is recognized by its identity in Telegram, not by its phone number, so a changed phone number still matches
>
> — `spec.md §5, AC-108, verbatim` · full text: [spec.md](../spec.md)

### AC-115 — domain invariant

> **Given** an Owner who already has as many Linked Accounts as the installation's limit allows
> **When** they try to add another account, or finish a wizard after another account has taken the last place
> **Then** teleX doesn't start the wizard, or ends the sign-in it just made so no teleX device remains in Telegram, and tells them the limit and that they can unlink an account to free a place; every Linked Account counts toward the limit, including one that has lost its session, signing in again to a lost-session account takes no new place, and lowering the limit never unlinks existing accounts
>
> — `spec.md §5, AC-115, verbatim` · full text: [spec.md](../spec.md)

### AC-03 — authorization

> **Given** two Owners, each with their own Linked Account
> **When** one Owner tries to see, re-sign-in to or unlink the other Owner's Linked Account, or to see any chat synced for it, by any means
> **Then** teleX behaves as if that account and those chats don't exist; and the Operator sees no Telegram name, phone number or chat of any Owner's Linked Account
>
> — `spec.md §5, AC-03, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `LinkedAccountId`, `ChannelId` typed ids at the `messaging` root (pattern: `identity/Ids.kt`)
- [ ] `internal/account/LinkedAccount` + `LinkedAccountState` (`connected`, `reconnecting`, `session_lost`) + `MaskedPhone(countryCode, lastDigits)`
- [ ] Pure decision function: given the authorized Telegram user, the caller, the optional "Sign in again" target, the existing account for that Telegram user and the count/limit → `NewLink | SignedInAgain | Refused(OTHER_OWNER | ALREADY_LINKED | LIMIT | MISMATCH)`; unit-test every branch
- [ ] `internal/account/LinkedAccountRows` repository on `JdbcClient` for the access patterns above, every query Owner-scoped except the session-id and Telegram-user lookups the lifecycle and the decision need
- [ ] `internal/config/`: `telex.telegram.max-accounts-per-owner` (`TELEX_TELEGRAM_MAX_ACCOUNTS_PER_OWNER`, default 3)
- [ ] The four event data classes at the module root; `LinkedAccounts.listMine` / `countMine` as the public read API
- [ ] `LinkedAccountRepositoryIT`: Owner scoping, chat count, advisory-lock count, lookups

## Edge cases

| Case | Behaviour |
|---|---|
| Same Telegram user, another Owner | `Refused(OTHER_OWNER)` (AC-04) |
| Same Telegram user, same Owner, connected | `Refused(ALREADY_LINKED)` (AC-108) |
| Same Telegram user, same Owner, Session lost | `SignedInAgain`, no limit check (AC-108, AC-117) |
| Sign in again, different Telegram user | `Refused(MISMATCH)` — or `OTHER_OWNER` if that user is another Owner's (AC-117) |
| Count = limit, new account | `Refused(LIMIT)`; Session lost accounts count; lowering the limit unlinks nothing (AC-115) |

## Definition of Done

- [ ] decision unit tests cover every branch above; `LinkedAccountRepositoryIT` passes
- [ ] every repository query filters on `owner_id` where the Owner is known
- [ ] `ModularityTest` green; detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
