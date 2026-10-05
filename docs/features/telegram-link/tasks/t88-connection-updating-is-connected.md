---
id: T88
title: "TDLib's Updating connection state counts as connected, not Reconnecting"
layer: "infra"
deps: []
blocks: ["T91"]
acs: ["AC-122"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (eleventh pass) — H-W20"
status: "todo"
---

# T88 — TDLib's Updating connection state counts as connected, not Reconnecting

## Origin

Follow-up from the eleventh-pass review: [`_review/review-2026-10-05-r3.md`](../_review/review-2026-10-05-r3.md), H-W20 (resolved "Fix now" by the user). Read that row first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-122
- **Blocked by:** — · **Blocks:** T91

## What to change

- `onConnection` (`TdlightSession.kt:268-271`) maps every state other than `connectionStateReady` to `SessionState.Connecting`, including `connectionStateUpdating`, which means connected and catching up. AC-122 limits Reconnecting to while Telegram is unreachable.
- Treat `connectionStateUpdating` as Ready.

## RED first

Unit, in `TdlightTelegramSessionsTest` next to `:446-448`: a `connectionStateUpdating` update after Ready emits no Connecting; after Connecting it emits Ready.

## Definition of Done

connectionStateUpdating maps to Ready, so an account catching up with Telegram shows Connected; only Connecting, ConnectingToProxy and WaitingForNetwork show Reconnecting. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
