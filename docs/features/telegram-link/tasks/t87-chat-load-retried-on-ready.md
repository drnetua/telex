---
id: T87
title: "A chat-list load that failed starts again on the next connection Ready, so sync and live chat changes resume"
layer: "infra"
deps: []
blocks: ["T91"]
acs: ["AC-116", "AC-121", "AC-122", "AC-36"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (eleventh pass) — H-W19"
status: "todo"
---

# T87 — A chat-list load that failed starts again on the next connection Ready, so sync and live chat changes resume

## Origin

Follow-up from the eleventh-pass review: [`_review/review-2026-10-05-r3.md`](../_review/review-2026-10-05-r3.md), H-W19 (resolved "Fix now" by the user). Read that row first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-116, AC-121, AC-122, AC-36
- **Blocked by:** — · **Blocks:** T91

## What to change

- `loadChats` (`TdlightSession.kt:405-437`) gives up for good on a 60 s batch timeout or a non-404 failure: `loadStarted` stays true, and `loadCompleted` never becomes true, so `afterChange` (`:337`) never flushes again and the account shows "Syncing chats: N of M" until teleX restarts.
- When a load stops without completing, clear `loadStarted`. On the next `connectionStateReady` while authorized and `!loadCompleted`, start the load again. Keep at most one load running.
- Update sad Flow 11 if its outage text no longer matches.

## RED first

Unit, in `TdlightTelegramSessionsTest`: the first `LoadChats` fails (or times out), then the connection goes Ready again. The load runs again and completes, the progress reaches the total, and a later `ChatTitle` update is stored.

## Definition of Done

A chat-list load that times out or fails is started again on the next connection Ready while the account is authorized; once it completes, later chat changes are stored. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
