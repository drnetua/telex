---
status: Draft
owner: "Anton Husiev (Backend Lead)"
reviewers: ["Tech Lead", "Security Lead"]
updated_at: "2026-10-03"
feature_size: M
---

# Events — telegram-link

Async contract for the flows drawn in `sad.md` §6. All events are internal to the one app process; there is no external broker. They travel on two channels, split by one rule:

> **Telegram data never enters `event_publication`.** The registry keeps completed publications (Modulith's default completion mode), so anything written there outlives an unlink. That would break spec §6 "0 stored items that identify the Telegram account … always". Registry events therefore carry teleX ids and states only. Events that need Telegram data (chat titles, Telegram ids) stay in memory (user decision, 2026-10-03).

## Channel 1: Modulith event publication registry (`event_publication` table)

- **Producer:** `messaging`, in the same transaction as the state change it announces.
- **Consumers:** `web/live` (SSE hints, ADR-0005) in E02. E09 `agents` and E20 `scheduling` subscribe to `AccountUnlinked` later (ADR-0001). E03, E04 and E17 may subscribe to `AccountLinked`.
- **Delivery:** at-least-once, after commit (`@ApplicationModuleListener`). Incomplete publications are republished on restart (`republish-outstanding-events-on-restart: true`).
- **Ordering:** none across events. Each listener is idempotent by `linkedAccountId`.

The Kotlin types live at the `messaging` module root (public API). The envelope fields below are the registry's columns, not part of the payload.

### Event: `messaging.account-linked.v1`

Kotlin `telex.messaging.AccountLinked`.

```json
{
  "event_id": "<event_publication.id — uuid>",
  "event_type": "telex.messaging.AccountLinked",
  "version": 1,
  "occurred_at": "<event_publication.publication_date — iso8601>",
  "data": {
    "ownerId": "<uuid — linked_account.owner_id>",
    "linkedAccountId": "<uuid — linked_account.id>"
  }
}
```

- **Origin:** Critical flow 1, "insert Linked Account Connected with masked phone, record AccountLinked".
- **Consumers in E02:** `web/live` → `linked-accounts` hint to the Owner's open streams.
- **Not published** for "Sign in again". That is the same account coming back, so it publishes `LinkedAccountStateChanged` instead (Flow 10).

### Event: `messaging.account-unlinked.v1`

Kotlin `telex.messaging.AccountUnlinked`. **Durable by contract** (AC-112, NFR-06): it is recorded in the transaction that deletes the account, so the announcement is exactly as durable as the deletion (ADR-0002).

```json
{
  "event_id": "<uuid>",
  "event_type": "telex.messaging.AccountUnlinked",
  "version": 1,
  "occurred_at": "<iso8601>",
  "data": {
    "ownerId": "<uuid — the deleted linked_account.owner_id>",
    "linkedAccountId": "<uuid — the deleted linked_account.id>"
  }
}
```

- **Origin:** Critical flow 2, "one transaction deletes the account, its sealed key and its chat list, records AccountUnlinked"; Flow 12.
- **Consumers:** `web/live` → `linked-accounts` hint (E02). From E09 / E20: pause the account's agents and cancel their scheduled runs (AC-05, moved by ADR-0001).
- **Kept after delivery:** yes. It carries only teleX ids, which spec §6 explicitly allows ("Records that carry only teleX's internal id of the Linked Account and no Telegram data (events, metrics) are kept").
- **Listener rule:** skip if this `linkedAccountId` was already handled, because a restart can deliver it again (Flow 12).

### Event: `messaging.linked-account-state-changed.v1`

Kotlin `telex.messaging.LinkedAccountStateChanged`.

```json
{
  "event_id": "<uuid>",
  "event_type": "telex.messaging.LinkedAccountStateChanged",
  "version": 1,
  "occurred_at": "<iso8601>",
  "data": {
    "ownerId": "<uuid — linked_account.owner_id>",
    "linkedAccountId": "<uuid — linked_account.id>",
    "state": "<connected | reconnecting | session_lost — linked_account.state>"
  }
}
```

- **Origin:** Critical flow 3 (boot reconnect), Flow 9 (Reconnecting / Session lost), Flow 10 (signed in again → `connected`).
- **Consumers in E02:** `web/live` → `linked-accounts` hint. E17 (Owner Bot "Telegram session lost", TG-14) subscribes later.
- **Published only on a real change** of the stored state, so a TDLib reconnect storm doesn't flood the registry.

### Event: `messaging.linked-account-sync-progressed.v1`

Kotlin `telex.messaging.LinkedAccountSyncProgressed`.

```json
{
  "event_id": "<uuid>",
  "event_type": "telex.messaging.LinkedAccountSyncProgressed",
  "version": 1,
  "occurred_at": "<iso8601>",
  "data": {
    "ownerId": "<uuid — linked_account.owner_id>",
    "linkedAccountId": "<uuid — linked_account.id>"
  }
}
```

- **Origin:** Flow 11, "record LinkedAccountSyncProgressed, at most one per second per account" (batches and later changes), and "mark the sync finished".
- **Payload carries no counts:** the SPA reads them from `listMyLinkedAccounts`, which keeps the counts out of the registry and makes a late delivery harmless.
- **Consumers in E02:** `web/live` → `linked-accounts` hint.
- **Throttle:** at most one per second per account, enforced by the producer (sad §8 Events). sad §11 accepts the registry growth; an after-commit, non-persistent listener is the documented fallback.

## Channel 2: in-process, non-durable (plain Spring application events)

- **Producer:** `telegram` (TDLib callbacks handed to a virtual thread, sad §8 Concurrency).
- **Consumer:** `messaging/internal/lifecycle` and `messaging/internal/channel`, via a synchronous `@EventListener` (not `@ApplicationModuleListener`), so nothing is written to `event_publication`.
- **Delivery:** at-most-once, in memory. A loss is repaired, not retried:
  - on restart, the boot reconnect re-reads every session's authorization state (Critical flow 3), and TDLib re-delivers the chat list on load (Flow 11);
  - while running, the next TDLib update for the same session or chat supersedes a missed one.
- **Ordering:** per Telegram session, by TDLib's sequence; `messaging` drops a state change whose sequence isn't newer (Flow 9).
- **Identity:** keyed by `TelegramSessionId`, never an `OwnerId` or a `LinkedAccountId` (ADR-0002). `messaging` maps the session to its account and drops events for a session it no longer knows (unlinked or replaced).

These are port types at the `telegram` module root. No JSON envelope is shown, because they are never serialised.

### Event: `telegram.session-state-changed`

Kotlin `telex.telegram.TelegramSessionStateChanged(sessionId: TelegramSessionId, state: Ready | Connecting | Closed, sequence: Long)`.

- **Origin:** Critical flow 3 ("state Ready / Connecting / Closed"), Flow 9 ("session state changed, with TDLib's sequence").
- **Mapping in `messaging`:** Ready → `connected`, Connecting → `reconnecting`, Closed → `session_lost`. Only Closed means Session lost, so an outage never looks like a lost session (AC-122).

### Event: `telegram.chats-changed`

Kotlin `telex.telegram.TelegramChatsChanged(sessionId: TelegramSessionId, upserted: List<ChatSnapshot>, removedChatIds: List<Long>, total: Int?, loadCompleted: Boolean)`, where `ChatSnapshot(chatId, type, title, folderIds, archived, unreadCount, order)` mirrors the `channel` columns.

- **Origin:** Critical flow 1 ("chats changed with batch and total"), Flow 11 ("a batch plus the account's total", "a chat joined, left or renamed").
- **Effect in `messaging`:**
  - upsert or delete `channel` rows by `(linked_account_id, telegram_chat_id)`;
  - set `linked_account.chats_total`, and `chat_sync_completed_at` when `loadCompleted`;
  - then publish `LinkedAccountSyncProgressed` (channel 1, throttled).
- **Carries Telegram data** (titles, chat ids), which is why it must never be persisted.

## Idempotency & retry

- **Channel 1 idempotency:** every listener is idempotent by `linkedAccountId`. A hint is harmless to repeat, and E09/E20 pausing an already-paused account is a no-op. A completed publication is never delivered again.
- **Channel 1 retry:** no backoff timer. Incomplete publications are resubmitted on every restart (Flow 12).
- **Channel 1 dead-letter:** none separate. An incomplete row in `event_publication` *is* the dead letter, visible by SQL and in health (sad §7 Monitoring).
- **Channel 2:** no retry and no dead letter, by design (see the repair rules above).

## Schema registry

- Registry: none. The Kotlin types at the `messaging` and `telegram` module roots are the schema, and the registry stores Jackson JSON for channel 1.
- Validator: `@ApplicationModuleTest` scenarios pin each channel 1 payload. The Flow 12 integration test (stop after commit, restart, assert delivery) pins `AccountUnlinked`'s durability (QG-1c). A test asserts that no `event_publication.serialized_event` contains a Telegram id or title after link, sync and unlink (QG-1b).
- **Backwards-compat policy:** additive only. Incomplete publications of an old type must still deserialise after an upgrade, so field removals wait until the registry holds none.
