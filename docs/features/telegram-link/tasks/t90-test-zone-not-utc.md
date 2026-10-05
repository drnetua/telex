---
id: T90
title: "Vitest runs in a non-UTC zone, so the device-zone fallback is tested"
layer: "tests"
deps: []
blocks: ["T91"]
acs: ["AC-02"]
files_hint: ["frontend/vite.config.ts", "frontend/src/pages/connect-telegram/Outcomes.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (eleventh pass) — T3"
status: "done"
---

# T90 — Vitest runs in a non-UTC zone, so the device-zone fallback is tested

## Origin

Follow-up from the eleventh-pass review: [`_review/review-2026-10-05-r3.md`](../_review/review-2026-10-05-r3.md), T3 (resolved "Fix now" by the user). Read that row first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-02
- **Blocked by:** — · **Blocks:** T91

## What to change

- `vite.config.ts:6` pins `TZ=UTC`, under which the device-zone fallback (`shell/time.ts:20-28`) prints the same as UTC: `timeZone ?? "UTC"` passes all 482 tests.
- Pin `Asia/Kathmandu` (+05:45, no DST, neither UTC nor Tokyo) and update the comment. Adjust any test that silently assumed UTC.

## RED first

Mutation check: with `time.ts:28` changed to `timeZone ?? "UTC"`, `Outcomes.test.tsx` fails; unmutated, the full suite is green.

## Definition of Done

Vitest pins TZ to Asia/Kathmandu; changing the formatInstant fallback from the device zone to UTC turns a test red. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
