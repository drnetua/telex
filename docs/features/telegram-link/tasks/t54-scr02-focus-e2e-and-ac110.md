---
id: T54
title: "SCR-02 moves focus to the outcome card, Playwright covers the refusal-ended and load-failed states, and a 401 mid-wizard reaches SCR-92"
layer: "ui"
deps: []
blocks: ["T55"]
acs: ["AC-107", "AC-109", "AC-110"]
files_hint: ["frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx", "frontend/src/pages/connect-telegram/Outcomes.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx", "e2e/tests/telegram-link.spec.ts", "e2e/support/telegram.ts"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (third pass) — findings W3, W5, D5"
status: "done"
---

# T54 — SCR-02 moves focus to the outcome card, Playwright covers the refusal-ended and load-failed states, and a 401 mid-wizard reaches SCR-92

## Origin

Follow-up from the third-pass review: [`_review/review-2026-10-04-r3.md`](../_review/review-2026-10-04-r3.md), findings **W3, W5, D5** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-107, AC-109, AC-110
- **Blocked by:** — · **Blocks:** T55

## What to change

- W3: when SCR-02 swaps a step for an outcome card (`phoneRefused` → `setOutcome`, code/resend refusals, 429, 409, 404) or mounts `LoadFailedState`, the focused input is removed and focus drops to `<body>`. Focus the card heading (`tabIndex={-1}` + ref + effect), as `AccountsPage.tsx:95,169` already does. Assert `toHaveFocus()` in the AC-107 cases and for load-failed.
- W5: expose the fake's `99965XYYYY` (unregistered once the code is checked) and, if practical, `99963XYYYY` (resend refusal) shapes in `SCENARIO` (`e2e/support/telegram.ts`; read `FakeTelegram.kt` KDoc for the exact shapes). Add a Playwright case that reaches the code step, submits a code, and asserts the "This linking has ended" heading plus the refusal body (take the copy from `messages.ts`), with `expectNoA11yViolations`; it runs in both phone and desktop projects. A load-failed e2e is optional if it can't be produced without mocking — say so if skipped.
- D5: AC-110's UI half has no test. Add a Vitest case in `ConnectTelegramPage.test.tsx` where a wizard call answers 401 `session-ended` and the session-ended screen (SCR-92) renders (use the same routing/FailureBoundary set-up other tests use).

## RED first

Vitest: `toHaveFocus()` on the outcome-card heading (red today); the 401 → SCR-92 case (may pass immediately if routing already works — then it is a coverage addition, say so). Playwright: the new case; run it if the e2e stack can be started locally, otherwise type-check it and say it runs first in CI.

## Definition of Done

A keyboard/screen-reader user hears the outcome card that replaces a step; the ended card naming the refusal runs in Playwright at both widths; a Vitest case drives SCR-02 to the session-ended screen on 401. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
