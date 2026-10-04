---
id: T64
title: "SCR-02 never focuses the step heading on the first load, and focuses it on every card-to-step change, including the banner's Sign in again"
layer: "ui"
deps: []
blocks: ["T65"]
acs: ["AC-109", "AC-122"]
files_hint: ["frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (fifth pass) — findings W11, W12"
status: "todo"
---

# T64 — SCR-02 never focuses the step heading on the first load, and focuses it on every card-to-step change, including the banner's Sign in again

## Origin

Follow-up from the fifth-pass review: [`_review/review-2026-10-04-r5.md`](../_review/review-2026-10-04-r5.md), findings **W11, W12** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-109, AC-122
- **Blocked by:** — · **Blocks:** T65

## What to change

- W11: `screens.md:58` and T59 say the step heading takes focus only after the user leaves an outcome or load-failed card, not on the first load. No test checks this; if `refocus` started as `true`, all tests would still pass.
- W12: `refocus` (`ConnectTelegramPage.tsx:65`) is a sticky boolean that is never reset, and the cache subscription that clears an outcome on the banner's Sign in again (`:72-85`) never sets it. So whether the new step's heading takes focus depends on whether this page already did a Start again or Try again.
- Fix: replace the sticky boolean with a per-transition signal (a counter that `Title` focuses on when it is > 0 and changes, or a flag consumed once), and bump it in all three places: Start again (`:127`), load-failed Try again (`:249-252`) and the cache subscription that clears an outcome.

## RED first

Vitest:
  1. Load `attempt()` (phone step): the "Connect your Telegram" heading does not have focus. This one passes today; prove it can fail by temporarily starting the signal as set, then restore (record it as a guard test).
  2. Show an outcome card (e.g. the ended card), then write a fresh attempt into the cache the way the Status Banner's Sign in again does (`client.setQueryData(linkingAttemptKey, fresh)`), without any prior Start again: the step heading has focus. Red today.

## Definition of Done

A test proves the first load of SCR-02 doesn't move focus to the step heading; the heading takes focus each time a step replaces an outcome or load-failed card, including when the Status Banner's Sign in again clears an outcome card, regardless of what happened earlier on the page. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
