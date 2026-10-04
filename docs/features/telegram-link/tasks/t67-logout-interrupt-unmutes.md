---
id: T67
title: "An interrupted log out unmutes the session, and the interrupt can't turn a finished unlink into a 500"
layer: "infra"
deps: []
blocks: ["T69"]
acs: ["AC-121", "AC-122", "AC-113"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightLogOutTest.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (sixth pass) — findings K13"
status: "todo"
---

# T67 — An interrupted log out unmutes the session, and the interrupt can't turn a finished unlink into a 500

## Origin

Follow-up from the sixth-pass review: [`_review/review-2026-10-04-r6.md`](../_review/review-2026-10-04-r6.md), findings **K13** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-121, AC-122, AC-113
- **Blocked by:** — · **Blocks:** T69

## What to change

- K13: `TdlightTelegramSessions.logOut` (`:147-155`) calls `abandonLogOut()` only on `TimeoutException`. An `InterruptedException` from `loggedOut.get` after `beginLogOut()` leaves `loggingOut = true`, so a later Closed stays muted. `LinkedAccounts.signOut` (`:167-169`) treats the interrupt as not confirmed and puts the flag back; if the delete then fails, the session stays open but muted (the K10 state again). On success, `release` (`:220`, `closed.get`) throws `InterruptedException` right after the row was deleted, so the Owner gets a 500 for an unlink that happened.
- Fix: in `logOut`, abandon the log out in a `finally` on every exit that isn't confirmed. In `release`, an interrupt while waiting for TDLib's Closed is logged, the flag is kept, and the session is still disposed — it doesn't throw.

## RED first

Unit (`TdlightLogOutTest`):
1. A Ready session whose LogOut TDLib never finishes; run `logOut` on a thread that gets interrupted while it waits. After it returns or throws, a later `Closed` from TDLib is announced as `Closed` (today it stays muted).
2. `close(id)` on a thread whose interrupt flag is set returns normally and disposes the session (today it throws `InterruptedException`).

## Definition of Done

A log out that is interrupted while waiting for TDLib leaves the session unmuted (a later Closed is announced), and closing a session on an interrupted thread disposes it without throwing. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
