---
id: T68
title: "SCR-02 does not focus the step heading when one step replaces another, and a test pins the guard"
layer: "ui"
deps: []
blocks: ["T69"]
acs: ["AC-109", "AC-122"]
files_hint: ["frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (sixth pass) — findings W13"
status: "done"
---

# T68 — SCR-02 does not focus the step heading when one step replaces another, and a test pins the guard

## Origin

Follow-up from the sixth-pass review: [`_review/review-2026-10-04-r6.md`](../_review/review-2026-10-04-r6.md), findings **W13** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-109, AC-122
- **Blocked by:** — · **Blocks:** T69

## What to change

- W13: the `cardShown` guard in the cache subscription (`ConnectTelegramPage.tsx:85`) is the only thing that keeps the in-page cache writes (`onNext` at `:277`, the step-mismatch refresh at `:178`) from bumping the focus counter. With the guard replaced by a plain `bumpRefocus()` all page tests pass and the heading takes focus on every phone → code change, against `screens.md:58`.
- Fix: a test only. No production change expected.

## RED first

Vitest: load the ended card, click Start again (the heading takes focus), move focus to the phone field, submit the phone; once the code step shows, assert the heading does not have focus. The test passes against the current code; prove it pins the guard by checking it goes red with the guard removed (record the mutation in the commit message).

## Definition of Done

A component test fails if SCR-02 moves focus to the step heading when one wizard step replaces another (phone to code), while Start again still focuses it. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
