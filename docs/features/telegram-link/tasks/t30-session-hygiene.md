---
id: T30
title: "Log out sign-ins Telegram authorized but teleX did not finish, and never hang on a failed client open"
layer: "infra"
deps: ["T29"]
blocks: ["T32", "T33", "T38"]
acs: ["AC-109", "AC-04"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/Linking.kt", "backend/app/src/main/kotlin/telex/telegram/TelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingAttemptIT.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-03 — findings Q3, Q5"
status: "done"
---

# T30 — Log out sign-ins Telegram authorized but teleX did not finish, and never hang on a failed client open

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **Q3, Q5** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-109, AC-04
- **Blocked by:** T29 · **Blocks:** T32, T33, T38

## What to change

- If Telegram authorized the sign-in but teleX did not finish it (code/password step timed out after 8 s while TDLib still reaches Ready; `GetMe`/`GetCallingCode` failed after Ready; `complete()` threw something other than `DuplicateKeyException`), the attempt stays on an authorized session and the 15-minute sweep / cancel later `discard`s it with close + destroy but no log-out, leaving a teleX device in the Telegram account. On any failure after authorization, do a bounded `logOut` and discard; or have `discard` always try a bounded `logOut` before `destroy` when the adapter reports the session authorized.
- `TdlightTelegramSessions` registers the session in its map before `facade.open`; if open throws, the entry keeps a `clientFuture` that never completes and `close`/`destroy`/`shutdown()` block forever on `clientFuture.get()`. Register only after attach (or remove in a catch) and use a bounded get in release.

## RED first

Unit (ScriptedTdlib) test: Ready reached after a step timeout, then cancel/expire → LogOut sent before destroy. Fake scenario + IT: complete() throws after authorization → session logged out. Unit test: facade.open throws → shutdown returns within a bound.

## Definition of Done

No authorized-but-abandoned session survives a discard; shutdown never hangs on a failed open. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
