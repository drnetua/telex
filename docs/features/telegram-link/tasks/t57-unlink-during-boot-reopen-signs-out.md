---
id: T57
title: "An unlink during the boot reopen window still signs teleX out of Telegram, and a failed reopen leaves no directory"
layer: "infra"
deps: ["T56"]
blocks: ["T58", "T60"]
acs: ["AC-111", "AC-113"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/main/kotlin/telex/messaging/internal/lifecycle/BootReconnect.kt", "backend/app/src/main/kotlin/telex/messaging/LinkedAccounts.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt", "backend/app/src/main/kotlin/telex/telegram/TelegramSessions.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightLogOutTest.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LifecycleIT.kt"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-04 (fourth pass) — findings K6, K7"
status: "done"
---

# T57 — An unlink during the boot reopen window still signs teleX out of Telegram, and a failed reopen leaves no directory

## Origin

Follow-up from the fourth-pass review: [`_review/review-2026-10-04-r4.md`](../_review/review-2026-10-04-r4.md), findings **K6, K7** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-111, AC-113
- **Blocked by:** T56 · **Blocks:** T58, T60

## What to change

- K6: a session enters `sessions` only after `facade.open` returns (`TdlightTelegramSessions.kt:121,174`). An unlink in the boot reopen window finds `sessions[id] == null` and sends no LogOut. `BootReconnect.dropIfUnlinked` (`BootReconnect.kt:79-90`) then only closes and destroys the session. An unlink before `rows.allNotSessionLost()` reads the row means no reopen at all. Either way the teleX device stays in Telegram's active sessions, which breaks AC-111 and the "0 active teleX devices" NFR.
- Fix: register an in-flight reopen marker before `facade.open`, and have `logOut` wait on it within its existing timeout, then sign out. And/or have `dropIfUnlinked` sign the reopened session out (logOut) before close + destroy. Cover the "before boot read the row" case too: e.g. `unlink` asks the port whether the session is known; if it isn't and the row has a sealed key and isn't SESSION_LOST, reopen with the unsealed key and logOut. Or BootReconnect handles it. Choose the design that keeps module boundaries (messaging → telegram port only).
- K7: the unlink's `destroy` deletes the directory, then `reopen`/`start` recreate it (`TdlightTelegramSessions.kt:65,163`). If `facade.open` then fails, nothing deletes it (`!reopened` guards the delete at `:169-171`, and `dropIfUnlinked` is skipped on the exception, `BootReconnect.kt:66-71`). On the failure path, delete the directory when no row holds the session.

## RED first

IT (`LifecycleIT`, fake): unlink while the reopen is in flight (the existing spy on `sealedKey`) and assert that the fake recorded a logOut for that session and that the unlink reported `signOutConfirmed=true`. It is red today: `dropIfUnlinked` only closes. Unit (`TdlightLogOutTest`): `logOut` on a session whose reopen is in flight waits and sends LogOut. Unit or IT: a reopen that fails after the unlink leaves no directory.

## Definition of Done

An unlink that races a boot reopen (reopen in flight, or before boot read the row) sends LogOut when Telegram is reachable, so signOutConfirmed is true and no teleX device stays in Telegram's active sessions; a reopen that fails after an unlink leaves no session directory. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
