---
id: T41
title: "Unregistered number with no replacement session answers 422, and Resend ends the attempt on every phone refusal"
layer: "app"
deps: ["T39"]
blocks: ["T45"]
acs: ["AC-107"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/Linking.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingStepsIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/LinkingApiIT.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 — findings N4, N5"
status: "done"
---

# T41 — Unregistered number with no replacement session answers 422, and Resend ends the attempt on every phone refusal

## Origin

Follow-up from the independent re-review: [`_review/review-2026-10-04.md`](../_review/review-2026-10-04.md), findings **N4, N5** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-107
- **Blocked by:** T39 · **Blocks:** T45

## What to change

- `Linking.replaceSession`: when the number is unregistered and no replacement session can be opened, it removes the attempt and rethrows `TelegramUnavailable` → 503, whose contract text says the attempt stays at its step. The Owner never learns the number is unregistered and the next submit gets 404. Once the attempt is ended, answer 422 `telegram-phone-unregistered` (the refusal that actually happened); keep the outcome metric.
- `resendCode` discards the attempt only for Unregistered; PhoneInvalid / PhoneBanned answer 422 `telegram-phone-*` while keeping the attempt, but the SPA (`steps.ts` `endsAttempt`) treats every 422 `telegram-phone-*` at the code step as the end — so "Start again" resumes the old attempt. Discard for all three phone refusals on resend, as `submitCode` does.

## RED first

LinkingStepsIT: set `fake.openUnavailable = true` after the phone step reports unregistered; assert 422 `telegram-phone-unregistered` and the attempt gone. LinkingStepsIT/LinkingApiIT: resend answering PhoneInvalid and PhoneBanned ends the attempt (GET → 404). Contract-validated over HTTP.

## Definition of Done

No 503 on an attempt that has already ended; server and SPA agree that every phone refusal ends the attempt. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
