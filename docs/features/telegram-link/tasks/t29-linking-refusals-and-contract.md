---
id: T29
title: "Linking refusals: end the attempt on an unregistered number, 503 when Telegram does not answer at start, clear codeLength, whitespace phone is 422"
layer: "app"
deps: ["T28", "T34"]
blocks: ["T30", "T35", "T38"]
acs: ["AC-107", "AC-106", "AC-119"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/Linking.kt", "backend/app/src/main/kotlin/telex/messaging/LinkingProblems.kt", "backend/app/src/main/kotlin/telex/web/api/LinkingController.kt", "backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt", "docs/features/telegram-link/contracts/openapi.yaml", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingStepsIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/LinkingApiIT.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt", "frontend/src/pages/connect-telegram/CodeStep.tsx", "frontend/src/pages/connect-telegram/outcome.ts", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-03 — findings S5, Q2, Q9, Q12"
status: "todo"
---

# T29 — Linking refusals: end the attempt on an unregistered number, 503 when Telegram does not answer at start, clear codeLength, whitespace phone is 422

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **S5, Q2, Q9, Q12** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-107, AC-106, AC-119
- **Blocked by:** T28, T34 · **Blocks:** T30, T35, T38

## What to change

- Unregistered number: TDLib reports it as `authorizationStateWaitRegistration` after `checkAuthenticationCode`; the adapter maps it to `PhoneUnregistered` and closes the client, but `submitCode` sends it to `refuseOrFail` whose `check(outcome is WaitRequired)` throws → 500. Handle `PhoneUnregistered`/`PhoneInvalid`/`PhoneBanned` at the code step: discard the attempt and answer the contract's refusal (`telegram-phone-unregistered` etc.). At the phone step the adapter also closes the client while `Linking` keeps the attempt open on the dead client (every retry → 8 s wait → 503): discard the attempt (or replace its session) so the next try starts fresh. The fake must close the session too and support WaitRegistration after the code.
- SPA: the SCR-02 code step maps the refusal codes it can now receive to the ended/refused outcome per screens.md (no new copy unless screens.md lacks it; any copy goes in messages.ts).
- `TelegramUnavailable` from `sessions.open` in `Linking.start` falls through to 500 `internal-error`. Map it to `TelegramUnavailableProblem` (503 `telegram-unavailable`) and add that 503 response to the start operation in `contracts/openapi.yaml`.
- When the attempt moves to PASSWORD, set `codeLength = null` (openapi: "null at other steps").
- A whitespace-only phone answers `400 validation-failed` (`@NotBlank`); the contract allows any `minLength: 1` string and says one with no digits answers `422 telegram-phone-invalid`. Use `@NotEmpty` (or equivalent) so the digit filter answers 422.

## RED first

Adapter unit test + fake scenario where WaitRegistration follows CheckCode → refusal, attempt gone; IT for phone-step unregistered then a second number succeeds; LinkingApiIT: start with Telegram not answering → 503 telegram-unavailable validated against the contract; `"codeLength":null` at PASSWORD; `"   "` → 422 telegram-phone-invalid; Vitest for the code-step refusal state.

## Definition of Done

No 500 on any refusal path; contract validator passes on the new responses; SPA shows the refusal at the code step. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
