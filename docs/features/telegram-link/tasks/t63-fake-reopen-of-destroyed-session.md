---
id: T63
title: "The fake reopens a destroyed session signed out, as TDLib does, and the boot-race test asserts the unconfirmed sign-out"
layer: "infra"
deps: ["T62"]
blocks: ["T65"]
acs: ["AC-111", "AC-113"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt", "backend/app/src/test/kotlin/telex/telegram/FakeTelegramTest.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LifecycleIT.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (fifth pass) — findings K11 (the trace reviewer's F1)"
status: "done"
---

# T63 — The fake reopens a destroyed session signed out, as TDLib does, and the boot-race test asserts the unconfirmed sign-out

## Origin

Follow-up from the fifth-pass review: [`_review/review-2026-10-04-r5.md`](../_review/review-2026-10-04-r5.md), findings **K11 (the trace reviewer's F1)** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-111, AC-113
- **Blocked by:** T62 · **Blocks:** T65

## What to change

- K11: in `LifecycleIT.kt:233-250` the unlink's own reopen fails, so the unlink deletes the row and destroys the directory, and only then does the boot reopen run. The fake's `destroy` (`FakeTelegram.kt:214-217`) removes the session from its map, and its `reopen` (`FakeTelegram.kt:72-85`) then does `computeIfAbsent { authorized = true }`, bringing back a signed-in session from a destroyed directory. So the test asserts "teleX device signed out of Telegram". Real TDLib opens an empty database there and asks for a phone number, so `TdlightSession.onWaitPhone` reports Closed and `logOut` returns false.
- Fix the fake: a `reopen` of a session with no directory (check before `directories.create(id)`), or one that was destroyed, comes up unauthorized and publishes `SessionState.Closed`, exactly like the signed-out branch already there. Sessions that only went through `simulateStop` (directory kept) still come back authorized.
- Rewrite the LifecycleIT case (`a reopen that completes after an unlink could not sign out…`) to assert the outcome the Owner sees: the unlink returned `signOutConfirmed=false`, no session is open, no directory remains. Rename it to say so. Check any other test that relied on the fake resurrecting a destroyed session and correct it (don't delete it).

## RED first

Unit (`FakeTelegramTest`): open a session, `destroy` it, `reopen` it: assert a Closed state is published and `logOut` returns false. Red today (it comes back Ready and logOut is true). Pair it with the existing real-adapter behaviour (`TdlightSession.onWaitPhone` → Closed) in a comment.
IT (`LifecycleIT`): the rewritten case asserts `signOutConfirmed=false`; red today because the fake reports a sign-out.

## Definition of Done

FakeTelegram.reopen of a session whose directory was destroyed (or never existed) comes up unauthorized and announces Closed, matching the real adapter on an empty TDLib database; the LifecycleIT boot-race case where the unlink's own reopen fails asserts signOutConfirmed=false, no open session and no directory, instead of a sign-out the real adapter can't perform. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
