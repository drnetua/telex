---
id: T32
title: "Cover the remaining contract responses over HTTP, run completions concurrently, and restart the app for real"
layer: "tests"
deps: ["T30", "T31"]
blocks: []
acs: ["AC-04", "AC-115", "AC-117", "AC-36", "AC-112"]
files_hint: ["backend/app/src/integrationTest/kotlin/telex/web/LinkingApiIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/LinkedAccountsApiIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/LiveUpdatesIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/ContractValidator.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingCompletionIT.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LifecycleIT.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkAnnouncementRestartIT.kt"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-03 — findings Q14, S12 (backend part)"
status: "done"
---

# T32 — Cover the remaining contract responses over HTTP, run completions concurrently, and restart the app for real

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **Q14, S12 (backend part)** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-04, AC-115, AC-117, AC-36, AC-112
- **Blocked by:** T30, T31 · **Blocks:** —

## What to change

- HTTP-level, contract-validated cases on the fake adapter for: the 409 refusals (`linked-account-limit-reached` with `limit`, `telegram-account-already-linked` on start and after authorization, `telegram-account-owned-by-another-owner`, `telegram-account-mismatch`), `outcome: signed-in-again`, 503 `telegram-unavailable`, and `/api/v1/live-updates` (headers/event shape as far as the validator supports SSE).
- Run two Owners' completions for the same Telegram account concurrently (two threads behind a latch) so the `DuplicateKeyException` → `afterRace` path and the advisory lock in `countMineLocked` actually run: assert exactly one row and the losing session logged out. Same for two completions of one Owner racing the account limit (AC-115).
- At least one IT that actually closes and reopens the Spring context against the same container and session directory (instead of `simulateStop` + `boot.run()` / manual `resubmitIncompletePublications`) and asserts AC-36 reconnect and AC-112 delivery of an outstanding `AccountUnlinked`.

## RED first

These are coverage tests over already-fixed behaviour: expect GOOD red only where a real bug surfaces; a false-pass is acceptable here ONLY if the test demonstrably exercises the path (e.g. assert the afterRace branch ran via a log/metric/latch). State this in the verdict.

## Definition of Done

Every declared response of the feature's operations is seen by the contract validator at least once; the race paths and a real restart are exercised. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
