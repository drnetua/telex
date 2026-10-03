---
id: T26
title: "Publish sync progress inside a transaction, write the sync time from the Clock, time the sync once"
layer: "app"
deps: []
blocks: ["T27"]
acs: ["AC-116", "AC-121"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/internal/channel/ChatListListener.kt", "backend/app/src/main/kotlin/telex/messaging/internal/channel/SyncProgressThrottle.kt", "backend/app/src/main/kotlin/telex/messaging/internal/channel/ChannelRows.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/ChatSyncIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/LiveUpdatesIT.kt", "backend/app/src/test/kotlin/telex/messaging/internal/channel/SyncProgressThrottleTest.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-03 — findings S1, Q10, Q11 (duration part)"
status: "todo"
---

# T26 — Publish sync progress inside a transaction, write the sync time from the Clock, time the sync once

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **S1, Q10, Q11 (duration part)** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-116, AC-121
- **Blocked by:** — · **Blocks:** T27

## What to change

- `LinkedAccountSyncProgressed` must be published inside a transaction (both the immediate publish and the throttle's trailing publish on its scheduler thread), so the `@ApplicationModuleListener` in `web` (`LiveHintListeners`) runs and the Modulith publication completes. Follow contracts/events.md ("in the same transaction as the state change it announces") and ADR-0005.
- `chat_sync_completed_at` is written from the injectable `Clock`, not SQL `now()` (data-model.md: no `DEFAULT now()`).
- `telex.chat_sync.duration` is recorded once, on the transition from not-completed to completed — not again on every later change that still carries `loadCompleted=true`.

## RED first

An integration test that sends a `TelegramChatsChanged` through the real path with NO surrounding transaction (no `tx.executeWithoutResult` wrapper, no plain-`@EventListener` probe) and asserts (a) an SSE `linked-accounts` hint reaches the Owner's stream and (b) no incomplete `event_publication` row remains. Fix `LiveUpdatesIT:118` so it no longer hides the bug. A fixed-clock assertion on `chatSyncCompletedAt`.

## Definition of Done

Sync progress reaches the SPA as a live hint with no surrounding transaction in the test; no incomplete event_publication rows after a sync; completedAt equals the fixed Clock; the duration timer has exactly one sample per completed sync. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
