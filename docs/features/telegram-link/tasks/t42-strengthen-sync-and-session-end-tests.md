---
id: T42
title: "Make the sync-in-transaction and AC-110 tests able to fail on their regressions"
layer: "tests"
deps: []
blocks: []
acs: ["AC-116", "AC-110"]
files_hint: ["backend/app/src/integrationTest/kotlin/telex/messaging/ChatSyncIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/LinkedAccountsApiIT.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 — findings T2, T3"
status: "todo"
---

# T42 — Make the sync-in-transaction and AC-110 tests able to fail on their regressions

## Origin

Follow-up from the independent re-review: [`_review/review-2026-10-04.md`](../_review/review-2026-10-04.md), findings **T2, T3** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-116, AC-110
- **Blocked by:** — · **Blocks:** —

## What to change

- `ChatSyncIT` "progress is published in a transaction" asserts zero incomplete `event_publication` rows, which also held before the fix (no row was written at all). Assert a *completed* `SyncProgressed` publication exists, or register an `@ApplicationModuleListener` probe and await delivery.
- The AC-110 test in `LinkedAccountsApiIT` (Sign-in Session ended → account keeps working) only checks the stored state. Also assert the Telegram session is still authorized after the sign-out, and push a fake chat change that then reaches the `channel` rows.

## RED first

Test-only task: there is no production change. Prove each strengthened test can fail by temporarily breaking the behaviour it guards (publish outside a tx; close the Telegram session on sign-out), record the quoted failing line, then restore the code. Do not commit the temporary breakage.

## Definition of Done

Each test fails when its guarded behaviour is removed and passes on the current code. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
