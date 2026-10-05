---
id: T96
title: "The chat-load retry's Telegram retry-after, doubling and caps each have a test"
layer: "tests"
deps: []
blocks: []
acs: ["AC-116", "AC-121"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightChatSyncTest.kt", "docs/features/telegram-link/test-plan.md", "docs/features/telegram-link/tasks.json", "docs/features/telegram-link/tasks/tracker.md"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (thirteenth pass) — T6, T7"
status: "todo"
---

# T96 — The chat-load retry's Telegram retry-after, doubling and caps each have a test

## Origin

Follow-up from the thirteenth-pass review: [`_review/review-2026-10-05-r5.md`](../_review/review-2026-10-05-r5.md), T6 and T7 (resolved "Fix now"). Read those rows first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-116, AC-121
- **Blocked by:** — · **Blocks:** —

## What to change

- T6: bound the flood test's completion await (`TdlightChatSyncTest.kt:377`) with `atMost(BEFORE_BACKOFF_MILLIS)`, so a retry that ignores Telegram's `retry after 1` and falls back to 5 s fails it. That also makes the two "retry not sent" tests meaningful.
- T7: extract the delay into a pure `internal` function in the tdlight package (no behaviour change) and unit-test it: 5, 10, 20 … s doubling to the 300 s cap; a 429 `retry after 3600` gives 300; `retry after 0` gives 1; a non-429 failure ignores a `retry after` text.
- Docs: test-plan row 88 names the pure-function test; mark T96 done in the tracker and this file.

## RED first

Mutation checks: dropping `retryAfter ?:`, removing the doubling, and removing the clamp each turn a test red.

## Definition of Done

Ignoring Telegram's retry-after, removing the doubling, or removing the 1–300 s clamp each turns a tdlight test red; test-plan row 88 claims only what the tests show; tasks.json matches every task file. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
