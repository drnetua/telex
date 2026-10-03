---
id: T31
title: "Make the SSE emitter registry add and remove atomic per Owner"
layer: "ports"
deps: []
blocks: ["T32"]
acs: ["AC-116", "AC-121", "AC-122"]
files_hint: ["backend/app/src/main/kotlin/telex/web/live/EmitterRegistry.kt", "backend/app/src/test/kotlin/telex/web/live/"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-03 — findings Q7"
status: "done"
---

# T31 — Make the SSE emitter registry add and remove atomic per Owner

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **Q7** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-116, AC-121, AC-122
- **Blocked by:** — · **Blocks:** T32

## What to change

- `EmitterRegistry.open` takes the existing set from `computeIfAbsent` while `remove` can empty that set and `streams.remove(ownerId, set)` it; the new stream is added to a set no longer in the map and never receives hints (heartbeat keeps it open). Do add and remove inside `streams.compute(ownerId) { … }` (or equivalent) so per-Owner updates are atomic.

## RED first

A concurrency unit test that interleaves open/remove for one Owner many times and asserts every open stream is reachable from the map (receives a hint).

## Definition of Done

No orphaned streams under concurrent open/remove. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
