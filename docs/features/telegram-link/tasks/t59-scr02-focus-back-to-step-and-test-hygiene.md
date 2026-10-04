---
id: T59
title: "SCR-02 moves focus back to the step after an outcome card, the wait card's buttons don't swap under focus, and the AC-110 and focus tests are sound"
layer: "ui"
deps: []
blocks: ["T60"]
acs: ["AC-107", "AC-109", "AC-02", "AC-108", "AC-110"]
files_hint: ["frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx", "frontend/src/pages/connect-telegram/Outcomes.tsx", "frontend/src/pages/connect-telegram/PhoneStep.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (fourth pass) — findings W7, W8, W9, W10"
status: "todo"
---

# T59 — SCR-02 moves focus back to the step after an outcome card, the wait card's buttons don't swap under focus, and the AC-110 and focus tests are sound

## Origin

Follow-up from the fourth-pass review: [`_review/review-2026-10-04-r4.md`](../_review/review-2026-10-04-r4.md), findings **W7, W8, W9, W10** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-107, AC-109, AC-02, AC-108, AC-110
- **Blocked by:** — · **Blocks:** T60

## What to change

- W7: "Start again" on the wait or ended card (`setOutcome(null)`, `ConnectTelegramPage.tsx:121`) and load-failed "Try again" (`:241-247`) unmount the focused card. The step then mounts without focus (`:265`; `PhoneStep.tsx:19` focuses only when `focusSignal > 0`), so focus falls to `<body>`. Focus the step heading (`tabIndex={-1}`, the same pattern as `EmptyState focusTitle`) when a step mounts after an outcome card or load-failed. Don't steal focus on the very first load.
- W8: the wait card's Back and Start again are the same DOM `<Button>` in a ternary (`Outcomes.tsx:47-55`). At 0:00 the focused Back silently becomes Start again. Give them distinct `key`s. If the focused button is replaced, move focus to the card heading.
- W9: the first AC-110 case (`ConnectTelegramPage.test.tsx:804`) doesn't mock `GET /api/v1/linked-accounts`, so it passes only by microtask order. Mock it.
- W10: retag "moves focus to the wait card on 429" as AC-02 (`:756`) and "moves focus to the refused card on 409" as AC-108 (`:767`).

## RED first

Vitest: after Start again from the ended card, the phone step heading `toHaveFocus()`, and the same after load-failed Try again. At 0:00 on the wait card with Back focused, focus isn't on a button now labelled Start again (it is on the heading or on a remounted button). Both are red today. W9 and W10 are test-only edits.

## Definition of Done

Start again / Try again move focus to the step heading; at 0:00 the focused Back button is not silently replaced by Start again; the first AC-110 case mocks every request it triggers; the focus tests carry the ACs they cover. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
