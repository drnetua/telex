---
id: T93
title: "The chat-load restart's guards and the Updating resume each have a test, and the moved tests leave no dead code"
layer: "tests"
deps: ["T92"]
blocks: ["T95"]
acs: ["AC-01", "AC-116", "AC-122"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightChatSyncTest.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (twelfth pass) — T5, H-T5, D25"
status: "done"
---

# T93 — The chat-load restart's guards and the Updating resume each have a test, and the moved tests leave no dead code

## Origin

Follow-up from the twelfth-pass review: [`_review/review-2026-10-05-r4.md`](../_review/review-2026-10-05-r4.md), T5, H-T5, D25 (resolved "Fix now" by the user). Read that row first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-01, AC-116, AC-122
- **Blocked by:** T92 · **Blocks:** T95

## What to change

- T5: `resumeChatLoad`'s guards (`TdlightSession.kt:416`) are untested. In the "loads nothing until startSync" test (`TdlightChatSyncTest.kt:131-150`), send `connectionStateUpdating` and `connectionStateReady` before `startSync` and assert no `LoadChats`. Add: a load fails, the session is closed, then Ready arrives, and no new `LoadChats` is sent.
- Skip the restart while teleX's own log out runs (`loggingOut`); `isAuthorized()` ignores it. Test it.
- H-T5: a load fails once and `connectionStateUpdating` alone completes it.
- D25: remove the unused `ChatType` import, the `chats()` and `chat(...)` helpers and the imports only they use from `TdlightTelegramSessionsTest.kt`.

## RED first

Mutation checks: removing `syncStarted &&`, removing `isAuthorized()`, resuming only on `CONNECTION_READY`, and removing the new `loggingOut` guard each turn a test red.

## Definition of Done

Removing the syncStarted guard, the authorization guard, or the Updating resume each turns a tdlight test red; a restart is skipped during teleX's own log out; TdlightTelegramSessionsTest has no unused imports or helpers. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)

## How it was done

- T92 had already added the `loggingOut` guard to `resumeChatLoad`, through `mayLoad()`, so T93 changes no production code. T93 adds one test for the connection path: a load fails, a log out starts, then Connecting and Ready arrive, and no new `LoadChats` is sent.
- The Updating test checks that the load completes within 3 s. The first backoff is 5 s, so only `connectionStateUpdating` can have finished the load in that time.
- Each test passed on its first run against the existing guards. Each of the four mutations named in the brief then failed a test: `syncStarted &&` (the startSync test), `isAuthorized()` (fail, close, then Ready), resume on `CONNECTION_READY` only (the Updating test), and `!loggingOut` (both log-out tests).
