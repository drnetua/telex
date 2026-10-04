---
id: T51
title: "teleX's own sign-out on unlink is not a Session lost, and is confirmed only when Telegram finishes it"
layer: "infra"
deps: []
blocks: ["T52", "T55"]
acs: ["AC-113", "AC-111", "AC-122"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt", "backend/app/src/main/kotlin/telex/telegram/TelegramSessions.kt", "backend/app/src/main/kotlin/telex/messaging/internal/lifecycle/SessionStateListener.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkIT.kt"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-04 (third pass) — findings K1, K4"
status: "todo"
---

# T51 — teleX's own sign-out on unlink is not a Session lost, and is confirmed only when Telegram finishes it

## Origin

Follow-up from the third-pass review: [`_review/review-2026-10-04-r3.md`](../_review/review-2026-10-04-r3.md), findings **K1, K4** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-113, AC-111, AC-122
- **Blocked by:** — · **Blocks:** T52, T55

## What to change

- K1: `TdlightTelegramSessions.logOut` sends `TdlibRequest.LogOut`; TDLib answers `authorizationStateLoggingOut`, and `TdlightSession.onAuthorization` (`STATE_LOGGING_OUT`, `authorized == true`) emits `SessionState.Closed`. `SessionStateListener` then moves the account being unlinked to `SESSION_LOST` (a live hint) and calls `telegram.close(id)` — `client.close()` while TDLib is still logging out. Our own close also completes `closed`, so `logOut` returns true without proof that Telegram accepted `auth.logOut`.
  - Mark a teleX-initiated logout on the session (e.g. `beginLogOut()` before sending `LogOut`). While it is set, `STATE_LOGGING_OUT` emits no `Closed` state and no listener path runs.
  - Confirm only when TDLib itself reports the logout finished (its terminal authorization state after `LoggingOut`, i.e. `authorizationStateClosed` reached through the logout, not our own `close()`). If `close()` / `release()` from our side ends the session before that, `logOut` returns false (unconfirmed).
  - Remote termination (no teleX logout in progress) must keep emitting `Closed` exactly as today (AC-117/AC-122 stay green).
  - `FakeTelegram.logOut` must match: no `TelegramSessionStateChanged(Closed)` for a teleX-initiated logout.
  - Document the contract on the `TelegramSessions.logOut` KDoc.
- K4: the two new `logOut` tests in `TdlightTelegramSessionsTest` (fresh session, reopened waits for Ready) use `supplyAsync` + `Thread.sleep`. Replace the sleep with a deterministic hand-off (a latch/test hook that proves the waiter is parked in `awaitAuthorized`, or a synchronous call for the fresh case) so the reopen test can't skip the wait and the fresh test can't flake.

## RED first

Unit (tdlight): after `logOut` sends `LogOut`, emitting `LoggingOut` publishes no `Closed` state; `logOut` returns true only after TDLib's terminal state; a local `close()` during the wait → false. Unit/IT: unlinking a connected account publishes no `LinkedAccountStateChanged` to `SESSION_LOST` (record events / assert the row is never `session_lost` before delete) — red today. Remote termination still yields Session lost.

## Definition of Done

Unlinking never flips the account to Session lost or closes the client mid-logout; signOutConfirmed is true only when TDLib finished the logout; the logOut tests have no sleeps. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
