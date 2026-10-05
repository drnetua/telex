---
id: T47
title: "Sign out a reopened session that has not reached Ready yet instead of skipping it"
layer: "infra"
deps: []
blocks: ["T50"]
acs: ["AC-113", "AC-111"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (second pass) — findings B6, B7"
status: "done"
---

# T47 — Sign out a reopened session that has not reached Ready yet instead of skipping it

## Origin

Follow-up from the second-pass review: [`_review/review-2026-10-04-r2.md`](../_review/review-2026-10-04-r2.md), findings **B6, B7** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-113, AC-111
- **Blocked by:** — · **Blocks:** T50

## What to change

- B6 (regression from T40): `TdlightTelegramSessions.logOut` sends `LogOut` only when `isAuthorized()`. A session reopened after a restart (`reopen`) that has not received `authorizationStateReady` yet is skipped, so an unlink in that window closes and destroys it locally and leaves a teleX device in Telegram. Refuse only when the session is really over (`lost || closing || closed.isDone`, or unknown). For an attached reopened session not yet Ready, wait (bounded by the same `timeout`) for Ready, then send `LogOut` and await `closed`; if Ready never comes within the timeout, return false (unconfirmed). Keep T40's guarantees: a remotely logged-out / closed / never-signed-in-new-session never confirms and never sends LogOut. Careful with the never-signed-in case: a fresh linking session waiting for a phone must still not be confirmed (distinguish reopened-from-key vs fresh if needed).
- B7: add the `99963XYYYY` resend-refusal test-number shape (X=0 invalid on resend, X=1 banned on resend) to the `FakeTelegram` class KDoc list.

## RED first

Unit: reopen a session with no Ready emitted, call logOut on a background thread, emit Ready then Closed → logOut sends LogOut and returns true (red today: returns false with no LogOut). Unit: Ready never arrives within the timeout → false, no LogOut. Keep the T40 tests green.

## Definition of Done

Unlink right after a restart signs the session out; no ended session is ever confirmed. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
