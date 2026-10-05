---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E02 telegram-link"
---

# 0002 — Own Linked Accounts and their chat list in `messaging`; keep `telegram` a session-only ACL

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

A Linked Account carries domain rules. One Telegram account belongs to one Owner (AC-04). There is no duplicate for the same Owner (AC-108). An installation-wide limit applies (AC-115). There are three states: Connected, Reconnecting and Session lost (AC-117, AC-122). A lost session keeps everything attached, while an unlink deletes everything (AC-111). The tech spec lists `telegram` as the publisher of `AccountLinked` / `AccountUnlinked`. But the module rules in the code forbid that shape. `telegram` may depend on `shared` only, and `web` may not call `telegram` at all (`telex/telegram/package-info.java`, `telex/web/package-info.java`). So every Owner action has to enter through a core module, and that module calls the `telegram` port.

## Decision drivers

- Spec §6 "Unlink leaves nothing": 0 stored items that identify the account, always, including the synced chat list.
- Spec §6 "Unlink announcement survives a restart" (tech-spec NFR-06), so E09 and E20 can build on it (ADR-0001).
- The existing module rules (integration → `shared` only; `web` → core only), checked by `ModularityTest`.
- E04 (chat reading) builds the chat list, history and search in `messaging` (tech spec: "Channel, Channel Set, message history").

## Considered options

1. **`messaging` owns the Linked Account and its chat list.** `web` calls `messaging`. `messaging` calls the `telegram` port for sign-in steps, client start/stop and sign-out, and calls `identity` for sealing keys (ADR-0003). `telegram` owns only Telegram sessions (TDLib clients and their files), keyed by its own `TelegramSessionId`, and reports back through return values and events.
2. **`identity` owns the Linked Account; `messaging` keeps the chat list, fed by events.** This puts the limit and the keys next to the Owner. But the unlink deletes the chat list only after an event is delivered, and `identity` takes on Telegram-specific state.
3. **A new core module `accounts` for the Linked Account; the chat list in `messaging` by events.** Responsibilities are cleanest, but this adds a 15th module and has the same event-delayed chat deletion as option 2.

## Decision outcome

**Chosen:** option 1. The account row, its sealed session key and every chat-list row live in one module, so an unlink deletes them in one database transaction. The same transaction records `AccountUnlinked` in the event publication registry. "Leaves nothing" then rests on a commit, not on event delivery, and the announcement is as durable as the deletion. `telegram` stays a pure ACL: it never sees an `OwnerId` or a `LinkedAccountId`. `messaging` maps its Linked Account to the current `TelegramSessionId`. A re-sign-in after a lost session swaps in a new `TelegramSessionId` under the same `LinkedAccountId` (AC-117).

## Consequences

**Positive**
- Unlink deletion is atomic and has no eventual-consistency window.
- E04 extends the same `channel` rows instead of migrating them from another module.
- The `telegram` module has no domain rules to test; it is tested against the TDLib facade or its fake.

**Negative**
- `messaging` grows a second aggregate (Linked Account next to Channel).
- The tech-spec event table must change: `AccountLinked` / `AccountUnlinked` are published by `messaging`, not `telegram` (§11 follow-up).
- `telegram` events carry a `TelegramSessionId`. `messaging` has to look up the Linked Account for every event, and it drops events for a session it no longer knows (one that was unlinked or replaced).

**Neutral**
- If `identity` later owns per-Owner quotas (E26), `messaging` reads the limit through `identity`'s public API instead of the installation config.

## Links

- Spec: [[../spec.md]] AC-03, AC-04, AC-108, AC-111, AC-112, AC-115, AC-117
- SAD: [[../sad.md]] §4, §5
- Related ADR: [[0001-move-agent-pause-on-unlink-to-agent-builder]], [[0003-seal-each-tdlib-database-key-with-a-stored-per-owner-key-under-an-installation-master-key]]
