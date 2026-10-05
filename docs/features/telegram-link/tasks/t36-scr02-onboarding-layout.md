---
id: T36
title: "Render SCR-02 in the confirmed onboarding card layout"
layer: "ui"
deps: ["T35"]
blocks: ["T33", "T38"]
acs: ["AC-01"]
files_hint: ["frontend/src/app/AppRoutes.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx", "frontend/src/app/"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-03 — findings S10"
status: "done"
---

# T36 — Render SCR-02 in the confirmed onboarding card layout

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **S10** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md) and the states in [screens.md](../screens.md).

- **ACs:** AC-01
- **Blocked by:** T35 · **Blocks:** T33, T38

## What to change

- SCR-02 sits under `AppLayout` (PageFrame header + `container-xl`) instead of the onboarding card layout the user confirmed in screens.md "Shared conventions" (decision of 2026-10-03, task t20): `page-center`, the 96 px logo, AuthLayout markup, with the Status Banner slot above the card. Add an onboarding layout route for `/connect-telegram` reusing the existing AuthLayout markup.

## RED first

Vitest: /connect-telegram renders inside the onboarding layout (logo, centered card, no PageFrame header) with the banner slot above the card.

## Definition of Done

SCR-02 matches screens.md shared conventions at phone and desktop widths. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
