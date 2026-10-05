---
id: T52
title: "An unlink during boot reopen leaves no live session, and the real resendCode reports phone refusals"
layer: "infra"
deps: ["T51"]
blocks: ["T55"]
acs: ["AC-111", "AC-113", "AC-107"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/internal/lifecycle/BootReconnect.kt", "backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/TelegramSessions.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LifecycleIT.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (third pass) — findings K2, K3"
status: "done"
---

# T52 — An unlink during boot reopen leaves no live session, and the real resendCode reports phone refusals

## Origin

Follow-up from the third-pass review: [`_review/review-2026-10-04-r3.md`](../_review/review-2026-10-04-r3.md), findings **K2, K3** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-111, AC-113, AC-107
- **Blocked by:** T51 · **Blocks:** T55

## What to change

- K2: boot reopens run asynchronously after `ApplicationReadyEvent`, and a session enters `sessions` only after `facade.open` returns. An unlink in that window finds `sessions[id] == null`: no LogOut, `close` is a no-op, `destroy` deletes the directory while `reopen` may still be opening it, and a finished reopen leaves a live client for a deleted account. Minimal fix: after `reopen` returns, `BootReconnect` re-reads the account row; if it no longer exists (or no longer holds this session), it closes and destroys the session. If you can, also make `logOut` for a session whose reopen is in flight wait within its timeout (an in-flight marker registered before `facade.open`), but the re-check is the required part.
- K3: tdlight `resendCode` maps every error except a flood wait to `TelegramUnavailable`. Route resend errors through `mapPhoneError` (flood first) so `PHONE_NUMBER_INVALID` / `PHONE_NUMBER_BANNED` (and unregistered if TDLib reports it) come back as the same outcomes the fake's `99963XYYYY` scenarios return. Add KDoc on `TelegramSessions.resendCode` listing its outcomes, and add `PhoneUnregistered` to the `checkCode` KDoc.

## RED first

Unit (tdlight): `ResendCode` failing with `PHONE_NUMBER_INVALID` → `PhoneInvalid`, `PHONE_NUMBER_BANNED` → `PhoneBanned`, flood still flood (red today: `TelegramUnavailable`). IT (or a focused unit on BootReconnect with the fake): an account deleted while its reopen is in flight ends with its session closed and destroyed (no open session for that id).

## Definition of Done

No TDLib client keeps running for an account unlinked while boot was reopening it; real resend refusals for invalid/banned numbers end the attempt as AC-107 says; port KDoc lists the outcomes. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
