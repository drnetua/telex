---
id: T58
title: "The real code step ends the attempt on an invalid or banned number instead of answering 503"
layer: "infra"
deps: ["T57"]
blocks: ["T60"]
acs: ["AC-107"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/TelegramSessions.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (fourth pass) — findings K8"
status: "todo"
---

# T58 — The real code step ends the attempt on an invalid or banned number instead of answering 503

## Origin

Follow-up from the fourth-pass review: [`_review/review-2026-10-04-r4.md`](../_review/review-2026-10-04-r4.md), findings **K8** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-107
- **Blocked by:** T57 · **Blocks:** T60

## What to change

- K8: `mapCodeError` (`TdlightTelegramSessions.kt:279-293`) maps only `PHONE_CODE_*` errors and flood waits; everything else throws `TelegramUnavailable`. A `PHONE_NUMBER_INVALID` or `PHONE_NUMBER_BANNED` from `checkAuthenticationCode` answers 503, and the attempt stays at the code step. AC-107 and `ux-flows.md:55,86` promise the ended card that names the refusal, and `Linking.kt:193` already handles all three outcomes.
- Fix: in `mapCodeError`, fall back to the phone mapping for `PHONE_NUMBER_*` errors (flood first). Keep the KDoc on `TelegramSessions.checkCode` in sync.

## RED first

Unit (`TdlightTelegramSessionsTest`, next to the resend case at `:274`): `CheckAuthenticationCode` failing with `PHONE_NUMBER_INVALID` → `PhoneInvalid`, and `PHONE_NUMBER_BANNED` → `PhoneBanned`. It is red today: `TelegramUnavailable`.

## Definition of Done

checkAuthenticationCode failing with PHONE_NUMBER_INVALID / PHONE_NUMBER_BANNED / PHONE_NUMBER_UNOCCUPIED maps to the phone refusal outcomes (flood first), as resend does since T52; the port KDoc lists them. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
