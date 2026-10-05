---
id: T37
title: "Reopen the live-update stream when the browser has closed it, with capped backoff"
layer: "ui"
deps: []
blocks: ["T38"]
acs: ["AC-116", "AC-117", "AC-122"]
files_hint: ["frontend/src/api/live.ts", "frontend/src/api/live.test.ts"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-03 — findings Q6"
status: "done"
---

# T37 — Reopen the live-update stream when the browser has closed it, with capped backoff

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **Q6** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md) and the states in [screens.md](../screens.md).

- **ACs:** AC-116, AC-117, AC-122
- **Screens:** SCR-10 `live`, SCR-60 `live` (the rows update in place after the stream reopens)
- **Blocked by:** — · **Blocks:** T38

## What to change

- The browser auto-retries an EventSource only after network errors; any non-200 / non-`text/event-stream` reconnect answer (502/503 from a proxy during a teleX restart, a 401) closes it for good, and the effect never re-runs. In the `error` handler, when `source.readyState === EventSource.CLOSED`, close and recreate with capped backoff; on reopen invalidate the queries the stream covers. Stop on unmount/sign-out.

## RED first

Vitest with a fake EventSource: a CLOSED error leads to a new EventSource after the backoff; backoff is capped; unmount cancels the timer.

## Definition of Done

Live state recovers after a teleX restart without a refocus. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
