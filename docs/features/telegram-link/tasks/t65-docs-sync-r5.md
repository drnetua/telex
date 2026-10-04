---
id: T65
title: "Sync sad, tracker, test-plan and spec with the fifth-review fixes"
layer: "docs"
deps: ["T61", "T62", "T63", "T64"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/sad.md", "docs/features/telegram-link/tasks/tracker.md", "docs/features/telegram-link/test-plan.md", "docs/features/telegram-link/screens.md", "docs/features/telegram-link/spec.md"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (fifth pass) — findings D9, D10, D11, plus the doc side of K9–K11 and W12"
status: "todo"
---

# T65 — Sync sad, tracker, test-plan and spec with the fifth-review fixes

## Origin

Follow-up from the fifth-pass review: [`_review/review-2026-10-04-r5.md`](../_review/review-2026-10-04-r5.md), findings **D9, D10, D11, plus the doc side of K9–K11 and W12** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** —
- **Blocked by:** T61, T62, T63, T64 · **Blocks:** —

## What to change

- D9: the AC-107 coverage row (`sad.md:835`) says "flow 5, flow 6 (resend)" and the Flow 6 heading (`sad.md:456`) names only AC-02. Since T58, flow 6 also has the branch where the code check refuses the phone. Change the row to "flow 5, flow 6 (resend and code check)" and add AC-107 to the Flow 6 heading.
- D10: a blank line between T38 and T39 (`tasks/tracker.md:46`) ends the Markdown table, so T39–T60 render as plain text. Delete it, and add T61–T65 rows (already added by planning; set them `done`) and update the total line.
- D11: `test-plan.md:24` names "the banner slot in `PageFrame`", which was deleted. Replace with the shell's `StatusBanner` in `AppShell` / `OnboardingLayout` (the C-04 `account-disconnected` condition).
- Doc side of the code fixes: sad flow 2 (`sad.md:~321`) now says a delete failure after a confirmed sign-out ends Session lost and an unconfirmed one keeps the session running (T62), and an unopenable key still deletes with an unconfirmed sign-out (T61). `screens.md:58`: the heading takes focus on every card-to-step change, including the banner's Sign in again (T64). Spec §8: update any open question these answer.

## RED first

Docs only, no test.

## Definition of Done

The sad AC-107 row and Flow 6 heading include the code-check refusal; the tracker table is one table; test-plan names the shell StatusBanner instead of PageFrame; sad flow 2 / screens describe the K9–K10 failure paths and the W12 focus rule as built. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
