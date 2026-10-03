---
id: T27
title: "Start chat sync only after the Linked Account is committed, report Telegram's real total, drop chats left meanwhile"
layer: "app"
deps: ["T26"]
blocks: ["T28", "T38"]
acs: ["AC-01", "AC-116", "AC-117", "AC-121"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/TelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/TelegramEvents.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt", "backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/main/kotlin/telex/messaging/internal/account/LinkCompletion.kt", "backend/app/src/main/kotlin/telex/messaging/Linking.kt", "backend/app/src/main/kotlin/telex/messaging/internal/channel/ChatListListener.kt", "backend/app/src/main/kotlin/telex/messaging/internal/channel/ChannelRows.kt", "backend/app/src/main/kotlin/telex/messaging/internal/lifecycle/SessionStateListener.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingCompletionIT.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/ChatSyncIT.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/NothingLeftBehindIT.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/ScriptedTdlib.kt", "backend/app/src/test/kotlin/telex/telegram/FakeTelegramTest.kt", "backend/telegram-tdlib/"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-03 — findings S2, S3, Q15"
status: "todo"
---

# T27 — Start chat sync only after the Linked Account is committed, report Telegram's real total, drop chats left meanwhile

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **S2, S3, Q15** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-01, AC-116, AC-117, AC-121
- **Blocked by:** T26 · **Blocks:** T28, T38

## What to change

- Both adapters emit chat-list (and state) events as soon as Telegram authorizes, before `LinkCompletion` inserts the row or swaps the session on Sign in again, so `ChatListListener`/`SessionStateListener` drop them and the adapter forgets them. Make sync start (or replay the adapter's full in-memory chat map and current state) only after the Linked Account row is committed — e.g. a port call such as `startSync(sessionId)` invoked after commit of `complete()` / `swapSession`. The fake must behave the same way. A Closed state right after link must end as Session lost, not stay Connected.
- Real adapter: `TdlightSession.flush` reports `total = chats.values.count { it.visible }` (loaded so far). Report Telegram's real total for the account, archived included (e.g. TDLib `updateUnreadChatCount.total_count` for Main + Archive, or `GetChats` total), and `null` until known. The SPA already renders unknown total as indeterminate.
- When a load completes, delete the account's `channel` rows for chats not in the loaded set (chats left while teleX was stopped or the session was lost), so `chatsSynced <= chatsTotal`.
- Correct `LinkingCompletionIT:163` (it asserts `chats_total` is null after linking — it codifies the bug) and remove the manual re-send workaround in `NothingLeftBehindIT:90-113` if no longer needed.

## RED first

IT: link through the wizard path on the fake (e.g. number 9996610101 = 101 chats) and wait until `chatsSynced == chatsTotal == 101`; IT: Sign in again re-syncs; IT: terminate right after link ends Session lost; adapter unit test (ScriptedTdlib) asserting the total mid-load is the TDLib total or null, never the loaded count; IT: a chat absent from a completed load is deleted.

## Definition of Done

A freshly linked account (and a signed-in-again one) syncs its full chat list with both adapters; total is Telegram's total or null; stale chats removed on load completion; no test asserts the old buggy behaviour. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
