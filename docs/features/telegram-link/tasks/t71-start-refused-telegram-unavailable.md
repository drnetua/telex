---
id: T71
title: "SCR-10 and SCR-60 each pin the start-refused toast for 503 telegram-unavailable"
layer: "ui"
deps: []
blocks: ["T72"]
acs: []
files_hint: ["frontend/src/pages/inbox/InboxPage.test.tsx", "frontend/src/pages/accounts/AccountsPage.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (seventh pass) — finding S1"
status: "done"
---

# T71 — SCR-10 and SCR-60 each pin the start-refused toast for 503 telegram-unavailable

## Origin

Follow-up from the seventh-pass review: [`_review/review-2026-10-04-r7.md`](../_review/review-2026-10-04-r7.md), finding **S1** (resolved "Fix now" by the user). The state is `start-refused` in [screens.md](../screens.md) SCR-10 (`:190`) and SCR-60 (`:243`); no AC names it.

- **ACs:** — (screens.md state)
- **Blocked by:** — · **Blocks:** T72

## What to change

- S1: the `503 telegram-unavailable` variant of `start-refused` has no page test on SCR-10 or SCR-60. Add one Vitest case per page. The behaviour is already built, so the test is a pin: check it with a mutation (drop the `telegram-unavailable` refusal copy) instead of a red run.

## RED first

Component (`InboxPage.test.tsx`, `AccountsPage.test.tsx`): the start call answers 503 `telegram-unavailable`; assert the toast "Telegram didn't answer. Check your connection and try again.", that the wizard doesn't open, and that SCR-93 doesn't cover the page.

## Definition of Done

Both pages have a test that fails if the telegram-unavailable start refusal stops showing its toast or opens the wizard. `pnpm run check` clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
