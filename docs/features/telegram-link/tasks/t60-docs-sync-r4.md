---
id: T60
title: "Sync screens.md, events.md, the T54 record, sad, ux-flows and spec with the fourth-review fixes"
layer: "docs"
deps: ["T56", "T57", "T58", "T59"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/screens.md", "docs/features/telegram-link/contracts/events.md", "docs/features/telegram-link/tasks/t54-scr02-focus-e2e-and-ac110.md", "docs/features/telegram-link/tasks/tracker.md", "docs/features/telegram-link/sad.md", "docs/features/telegram-link/ux-flows.md", "docs/features/telegram-link/spec.md"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (fourth pass) — findings D6, D7, D8, plus the doc side of K5–K8 and W7–W8"
status: "done"
---

# T60 — Sync screens.md, events.md, the T54 record, sad, ux-flows and spec with the fourth-review fixes

## Origin

Follow-up from the fourth-pass review: [`_review/review-2026-10-04-r4.md`](../_review/review-2026-10-04-r4.md), findings **D6, D7, D8, plus the doc side of K5–K8 and W7–W8** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** —
- **Blocked by:** T56, T57, T58, T59 · **Blocks:** —

## What to change

- D6: `screens.md:39` says the banner's refusal Toast is announced through the persistent polite region. It is an error Toast, so it is its own `role=alert` outside that region (`Toast.tsx:80,107`). Say that info Toasts use the polite region and error Toasts are their own alert.
- D7: T54's title and `tracker.md:62` claim a load-failed Playwright test that doesn't exist. Record the skip in the T54 file: load-failed needs a mocked pulse, and Vitest covers it at `ConnectTelegramPage.test.tsx:793`. Don't retitle history silently; add a note.
- D8: under `telegram.session-state-changed` in `contracts/events.md:127-128`, add: never emitted for teleX's own log out, so `Closed` always means Telegram ended the session.
- Record in sad (flow 2 / §8 crosscutting or the deviation list) and ux-flows what T56–T59 changed: the unlink failure path after sign-out, the boot-window sign-out, code-step phone refusals, and focus returning to the step. Spec §8: close or update any open question these answer.

## RED first

Docs only, no test.

## Definition of Done

screens.md, events.md, sad, ux-flows, spec §8 and the T54 record describe the code as built after T56–T59. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
