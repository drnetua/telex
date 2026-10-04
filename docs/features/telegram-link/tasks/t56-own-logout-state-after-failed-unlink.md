---
id: T56
title: "A session whose unlink failed after teleX's own sign-out still reports its state, and the fake behaves the same"
layer: "infra"
deps: []
blocks: ["T57", "T60"]
acs: ["AC-122", "AC-117", "AC-113"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/main/kotlin/telex/messaging/LinkedAccounts.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt", "backend/app/src/main/kotlin/telex/telegram/TelegramSessions.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightLogOutTest.kt", "backend/app/src/test/kotlin/telex/telegram/FakeTelegramTest.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkIT.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (fourth pass) — findings K5"
status: "todo"
---

# T56 — A session whose unlink failed after teleX's own sign-out still reports its state, and the fake behaves the same

## Origin

Follow-up from the fourth-pass review: [`_review/review-2026-10-04-r4.md`](../_review/review-2026-10-04-r4.md), findings **K5** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-122, AC-117, AC-113
- **Blocked by:** — · **Blocks:** T57, T60

## What to change

- K5: `TdlightSession.beginLogOut()` sets `loggingOut`, and nothing ever clears it, so `emitState` stays muted for the rest of the session (`TdlightSession.kt:146-150,284`). `LinkedAccounts.unlink` closes the session only after `deletion.delete` succeeds (`LinkedAccounts.kt:75-86`). If the delete throws, the row survives with a muted client: when TDLib finishes the logout, neither Closed nor Session lost is published.
- Fix (pick the smaller one that holds):
  - in `unlink`, when `deletion.delete` throws after a sign-out was attempted, close the session (and let the account surface as Session lost through the normal path, or transition it), then rethrow; or
  - scope the suppression: clear `loggingOut` when `loggedOut` completes false while the session isn't closing, so a later Closed is announced.
- Make the fake match the real adapter after an unconfirmed `logOut`. Today the fake only skips the one Closed inside `logOut`, then still publishes Ready on `restoreConnectivity` and Closed on `terminate`. Document the behaviour on the `TelegramSessions.logOut` KDoc.

## RED first

IT (`UnlinkIT`, fake): make `deletion.delete` throw once (spy) after logOut. Assert that the account doesn't end up `connected` with a client that will never report again. Pick the observable outcome the fix guarantees, e.g. the account is Session lost, or the session is closed and a retried unlink succeeds. Also add a unit pair (`TdlightLogOutTest` + `FakeTelegramTest`) asserting the same post-unconfirmed-logOut state behaviour on both adapters. It is red today on the real adapter (muted forever).

## Definition of Done

If deletion.delete throws after logOut, the account is not left with a muted dead client: it reaches Session lost (or its session is closed) instead of staying connected/reconnecting forever; the tdlight adapter and the fake agree after an unconfirmed logOut. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
