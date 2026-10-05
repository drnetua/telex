---
id: T85
title: "Vitest runs in UTC, and a malformed Retry-After falls back to retryAt"
layer: "tests"
deps: []
blocks: ["T86"]
acs: ["AC-02"]
files_hint: ["frontend/vite.config.ts", "frontend/src/api/linking.test.ts"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (tenth pass) — T1, T2"
status: "todo"
---

# T85 — Vitest runs in UTC, and a malformed Retry-After falls back to retryAt

## Origin

Follow-up from the tenth-pass review: [`_review/review-2026-10-05-r2.md`](../_review/review-2026-10-05-r2.md), T1, T2 (resolved "Fix now" by the user). Read those rows first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-02
- **Blocked by:** — · **Blocks:** T86

## What to change

- T1: nothing pins the device zone for Vitest, so the Tokyo test is meaningless on a host in UTC+9. Pin `TZ=UTC` in `vite.config.ts` before any test code runs.
- T2: no test covers the guard in `retryAfter` (`client.ts:64-67`). Add a `linking.test.ts` case: an HTTP-date and `abc` each give `retryAfterSeconds: undefined`.

## RED first

Tests only. Prove each by mutation: with the guard removed, the new `linking.test.ts` case goes red; with the old device-zone `clock()` and the host at `TZ=Asia/Tokyo`, the Tokyo test goes red.

## Definition of Done

The zone tests hold on any host, and a malformed `Retry-After` is proven to fall back to `retryAt`. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
