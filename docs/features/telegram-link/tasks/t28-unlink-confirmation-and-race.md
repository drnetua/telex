---
id: T28
title: "Unlink: report an unconfirmed sign-out for Session lost, and sign out the session the delete actually removed"
layer: "app"
deps: ["T27"]
blocks: ["T29"]
acs: ["AC-111", "AC-113"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/LinkedAccounts.kt", "backend/app/src/main/kotlin/telex/messaging/internal/account/LinkedAccountRows.kt", "backend/app/src/main/kotlin/telex/messaging/internal/account/AccountDeletion.kt", "backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkIT.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-03 — findings S4, Q4"
status: "todo"
---

# T28 — Unlink: report an unconfirmed sign-out for Session lost, and sign out the session the delete actually removed

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **S4, Q4** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-111, AC-113
- **Blocked by:** T27 · **Blocks:** T29

## What to change

- `TdlightTelegramSessions.logOut` returns `true` for an unknown/closed session (`sessions[id] ?: return true`) so unlinking a Session lost account reports `signOutConfirmed=true`. Make `unlink` skip the sign-out and report unconfirmed when `account.state == SESSION_LOST`, and make both adapters' `logOut` return `false` for a session that is not open (fake and real must agree).
- `unlink` reads `telegramSessionId` before `discardTargeting` takes the Owner lock; a Sign in again completing in between swaps to a new session S2 that is never signed out or destroyed. Discard the targeting attempt first, then read the session — or delete with `RETURNING telegram_session_id` and sign out + destroy exactly that session (keep ADR-0002 ordering: bounded log-out, one-tx delete + AccountUnlinked, then destroy).
- Fix `UnlinkIT:194` which builds a state the code never produces (`telegram_session_id = NULL`); use a session lost through `fake.terminate(...)`.

## RED first

IT: lose the session via `fake.terminate`, unlink, assert `signOutConfirmed=false` (and the same on the real adapter via a unit test of `logOut` for an unknown session). IT: unlink racing a completing Sign in again leaves no open/authorized session and no directory.

## Definition of Done

Session lost unlink reports unconfirmed with both adapters; the unlink/sign-in-again race leaves nothing behind. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
