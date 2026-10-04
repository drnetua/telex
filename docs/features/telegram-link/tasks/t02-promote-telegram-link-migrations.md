---
id: T2
title: "Promote the owner_key, linked_account and channel migrations into the live Flyway tree"
layer: "migration"
deps: []
blocks: ["T3", "T7"]
acs: ["AC-04", "AC-108", "AC-111"]
files_hint: ["docs/features/telegram-link/migrations/01_create_owner_key.up.sql", "docs/features/telegram-link/migrations/01_create_owner_key.down.sql", "docs/features/telegram-link/migrations/02_create_linked_account.up.sql", "docs/features/telegram-link/migrations/02_create_linked_account.down.sql", "docs/features/telegram-link/migrations/03_create_channel.up.sql", "docs/features/telegram-link/migrations/03_create_channel.down.sql"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "done"
---

# T2 — Promote the owner_key, linked_account and channel migrations into the live Flyway tree

## Place in the sequence

- **Blocked by:** — · **Blocks:** T3 — Add OwnerKeys envelope encryption with the master-key check and reset, and SignInSessions.isLive, T7 — Build the LinkedAccount aggregate: rules, states, masked phone, repository, events and the account limit · **Wave:** 1 — no deps — starts in wave 1 beside the spike and the fake adapter.
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to unlink a Linked Account so that teleX signs out of it and deletes everything it stored for it
> **So that** no copy of my Telegram access stays in teleX
>
> — `spec.md §4, US-03, verbatim` · full text: [spec.md](../spec.md)

The schema makes "one Telegram account belongs to one Owner" and "an unlink deletes the chat list with the account" hold at the database level.

## Inlined context

> `messaging` owns `linked_account` and `channel`. `identity` owns `owner_key`. `telegram` owns no table […]. The Modulith `event_publication` table (baseline) carries […] and needs nothing new.
> - Time: `TIMESTAMP WITH TIME ZONE`, written from the injectable `Clock`. No `DEFAULT now()`.
> - Delete strategy: hard delete. Unlink deletes the account, its sealed key and its chat list in one transaction (ADR-0002); nothing identifying the Telegram account survives.
>
> — `data-model.md §intro + Conventions applied, abridged` · full text: [data-model.md](../data-model.md)

> Migrations: `backend/app/src/main/resources/db/migration/V<yyyyMMddHHmm>__<name>.sql`, each with a rollback `db/rollback/U<same-version>__<name>.sql`; `MigrationRollbackIT` applies up → down → up for all of them. Feature migrations are staged by `/sdd:data-model` in `docs/features/<slug>/migrations/` and promoted by `implement`.
>
> — `CLAUDE.md §Layout and code conventions, Migrations, verbatim` · full text: [CLAUDE.md](../../../../CLAUDE.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

Staged pairs (promote in this order; suggested versions after the latest live `V202610021600`):

| Staged pair | Live up / rollback |
|---|---|
| `migrations/01_create_owner_key.{up,down}.sql` | `V202610031200__create_owner_key.sql` / `U202610031200__create_owner_key.sql` |
| `migrations/02_create_linked_account.{up,down}.sql` | `V202610031201__create_linked_account.sql` / `U…1201__…` |
| `migrations/03_create_channel.{up,down}.sql` | `V202610031202__create_channel.sql` / `U…1202__…` |

| Table | Key constraints / indexes | Change |
|---|---|---|
| `owner_key` | PK `owner_id` FK → `owner(id)`; `sealed_key` CHECK `octet_length = 60` | added |
| `linked_account` | `linked_account_telegram_user_id_uq` (installation-wide, AC-04/AC-108); `linked_account_telegram_session_id_uq`; `linked_account_owner_id_idx`; `UNIQUE (id, owner_id)`; state CHECK IN (`connected`,`reconnecting`,`session_lost`); `num_nonnulls(telegram_session_id, tdlib_key_sealed) IN (0,2)`; session required unless `session_lost`; phone CHECKs `^[0-9]{1,3}$` / `^[0-9]{2}$` | added |
| `channel` | composite FK `(linked_account_id, owner_id)` → `linked_account(id, owner_id)` ON DELETE CASCADE (AC-111); `channel_linked_account_telegram_chat_uq`; type CHECK; `unread_count >= 0` | added |

— `data-model.md §Entities + §Indexes, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no API surface.

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

### AC-111 — happy

> **Given** an Owner with a connected Linked Account
> **When** they choose to unlink it and confirm in a dialog that names what will happen
> **Then** teleX signs out of that Telegram account so the teleX device disappears from the account's active sessions in Telegram, deletes the account's session and every chat-list entry it synced, and removes the account from the list; if it was the Owner's last Linked Account, the Inbox shows the "Connect Telegram" step again
>
> — `spec.md §5, AC-111, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Copy each staged `.up.sql` to `backend/app/src/main/resources/db/migration/V<version>__<name>.sql` and its `.down.sql` to `db/rollback/U<version>__<name>.sql`, unchanged
- [ ] Extend `MigrationRollbackIT` expectations if it lists versions explicitly (`backend/app/src/integrationTest/kotlin/telex/MigrationRollbackIT.kt`)
- [ ] Add `backend/app/src/integrationTest/kotlin/telex/messaging/MessagingSchemaIT.kt` (pattern: `IdentitySchemaIT`): second row with the same `telegram_user_id` fails; deleting a `linked_account` deletes its `channel` rows; a `channel` row with another Owner's `owner_id` is rejected by the composite FK; `num_nonnulls` and state CHECKs reject bad rows

## Edge cases

| Case | Behaviour |
|---|---|
| Two Owners insert the same `telegram_user_id` | Unique violation — the app maps it to the AC-04 refusal (T10) |
| `channel` row pointing at an account of another Owner | Rejected by the composite FK (AC-03 at the DB level) |
| Rollback after data exists | `down` drops the tables (channel → linked_account → owner_key order is enforced by promoting in reverse) |

## Definition of Done

- [ ] staged migrations are promoted to live `migrations/`, then apply and revert cleanly (`MigrationRollbackIT` up → down → up)
- [ ] `MessagingSchemaIT` covers the uniqueness, cascade and composite-FK rules
- [ ] `./gradlew integrationTest` green
- [ ] every Hard Rule inlined above still holds
