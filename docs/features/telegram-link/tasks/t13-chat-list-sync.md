---
id: T13
title: "Sync each account's chat list into channel rows with throttled progress events"
layer: "app"
deps: ["T4", "T7"]
blocks: ["T24"]
acs: ["AC-116", "AC-121"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/internal/channel/", "backend/app/src/integrationTest/kotlin/telex/messaging/ChatSyncIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T13 — Sync each account's chat list into channel rows with throttled progress events

## Place in the sequence

- **Blocked by:** T4 — Define the TelegramSessions port, its in-process events and the fake Telegram adapter, T7 — Build the LinkedAccount aggregate: rules, states, masked phone, repository, events and the account limit · **Blocks:** T24 — Prove nothing is left behind or leaked: unlink dump, restart delivery, registry and log scans · **Wave:** 3 — needs the port events (T4) and the repository (T7); parallel to the wizard chain.
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to see whether each Linked Account is connected (and whether it is still syncing its chats), is reconnecting after Telegram was unreachable, or has lost its session, and to sign in to it again when it has
> **So that** I know when teleX can work with an account and fix it without starting over
>
> — `spec.md §4, US-51, verbatim` · full text: [spec.md](../spec.md)

This task gives the Owner visible sync progress and a chat count that stays current while the account is connected.

## Inlined context

> Flow 11: upsert by Telegram chat id, so a chat seen again is not counted twice → upsert the chat-list rows, store the synced count and the total → record LinkedAccountSyncProgressed, at most one per second per account → mark the sync finished. The sync runs in the service whether or not a page is open. A restart mid-sync resumes from the stored counts, not from zero. While the account is connected: a chat joined, left or renamed, or new messages changed a chat's order or unread count → upsert or remove the chat-list row and update the count → LinkedAccountSyncProgressed; the count is current within a minute.
>
> — `sad.md §6, Flow 11, abridged` · full text: [sad.md](../sad.md)

> *Synced chat count* — derived as `COUNT(*)` of the account's `channel` rows, so it can't drift from the rows and follows joins and leaves by itself (AC-116, AC-121). Only Telegram's reported total and the "finished" time are stored.
> Payload carries no counts: the SPA reads them from `listMyLinkedAccounts`.
>
> — `data-model.md §Not stored + events.md, abridged` · full text: [data-model.md](../data-model.md)

> **Hard rule:** Telegram data never enters `event_publication`: `TelegramChatsChanged` is consumed by a synchronous `@EventListener`; `LinkedAccountSyncProgressed` carries ids only. Chat titles are never logged.
>
> — `contracts/events.md §intro + sad.md §8 Logging, abridged` · full text: [events.md](../contracts/events.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Column | Change |
|---|---|
| `channel` (`id` UUIDv7 stable across upserts, `owner_id`, `linked_account_id`, `telegram_chat_id`, `type`, `title`, `folder_ids`, `archived`, `unread_count`, `chat_order`) | upsert `ON CONFLICT (linked_account_id, telegram_chat_id)`; delete removed chats |
| `linked_account.chats_total` | set from the batch `total` (archived included) |
| `linked_account.chat_sync_completed_at` | set when `loadCompleted` |

— `data-model.md §channel, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no API surface. (The counts surface through `listMyLinkedAccounts.chatSync` in T14.)

## Acceptance criteria

### AC-116 — happy

> **Given** an Owner who just linked an account
> **When** its chat list is syncing
> **Then** the connected account shows the sync progress as chats synced out of the total, where the total counts every chat of the account, archived ones included, and when the sync finishes it shows the number of chats; the Owner can leave the page and come back without stopping the sync, and if teleX restarts during the sync it continues from where it stopped instead of starting over
>
> — `spec.md §5, AC-116, verbatim` · full text: [spec.md](../spec.md)

### AC-121 — happy

> **Given** an Owner with a connected Linked Account whose chat list has finished syncing
> **When** in Telegram they join or leave a chat, a chat is renamed, or new messages arrive
> **Then** within one minute the number of chats shown for the account reflects the change, and the chat list teleX keeps for the account stays current while it is connected; showing that list and the messages is E04
>
> — `spec.md §5, AC-121, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `internal/channel/ChatListListener` (`@EventListener` on `TelegramChatsChanged`): map session → account (drop unknown); upsert/delete rows in one transaction; set `chats_total` / `chat_sync_completed_at`
- [ ] Map `ChatSnapshot.type` to the `channel.type` CHECK values (a supergroup with `is_channel` → `channel`)
- [ ] Throttle `LinkedAccountSyncProgressed` to ≤ 1/s per account with a trailing publish so the last change is never lost (injectable `Clock`); metric `telex.chat_sync.duration`
- [ ] `ChatSyncIT` (fake, 500 chats with 50 archived): progress counts, completion, repeated batches don't double-count, join/leave/rename updates the count, restart mid-sync resumes without starting from zero

## Edge cases

| Case | Behaviour |
|---|---|
| Same chat delivered twice | One row (upsert key) — counted once |
| Total not yet reported | `chats_total` NULL → SPA shows indeterminate progress |
| Chat left in Telegram | Row deleted; count drops within a minute (AC-121) |
| Event for an unlinked account | Dropped; no rows written |

## Definition of Done

- [ ] `ChatSyncIT` passes; 500 chats complete on the fake
- [ ] no chat title or Telegram id in `event_publication` (asserted again in T24)
- [ ] detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
