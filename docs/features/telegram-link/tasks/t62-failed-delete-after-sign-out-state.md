---
id: T62
title: "A failed delete after the sign-out leaves the account Session lost when Telegram confirmed it, and keeps an unconfirmed session running"
layer: "infra"
deps: ["T61"]
blocks: ["T63", "T65"]
acs: ["AC-122", "AC-121", "AC-113"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/LinkedAccounts.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkIT.kt", "backend/app/src/main/kotlin/telex/messaging/internal/lifecycle/SessionStateListener.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (fifth pass) — findings K10 (and the trace reviewer's F2)"
status: "todo"
---

# T62 — A failed delete after the sign-out leaves the account Session lost when Telegram confirmed it, and keeps an unconfirmed session running

## Origin

Follow-up from the fifth-pass review: [`_review/review-2026-10-04-r5.md`](../_review/review-2026-10-04-r5.md), findings **K10 (and the trace reviewer's F2)** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-122, AC-121, AC-113
- **Blocked by:** T61 · **Blocks:** T63, T65

## What to change

- K10: when `deletion.delete` throws, `deleteOrClose` (`LinkedAccounts.kt:97-110`) closes the session whatever the sign-out returned:
  - **Sign-out confirmed:** the client is gone and `emitState` is muted, so the row stays `connected` or `reconnecting` with no client and no chat sync until a restart or a retried unlink.
  - **Sign-out not confirmed:** `abandonLogOut` (`TdlightTelegramSessions.kt:152-154`) had already unmuted a valid session that would reconnect by itself. Closing it kills a working client, and `markClosing` (`TdlightSession.kt:274`) mutes the late Closed that `abandonLogOut` was meant to let through.
- Fix: pass `confirmed` into `deleteOrClose`.
  - Confirmed: close the session, then move the row to `SESSION_LOST` (Telegram really ended it; reuse the transition the session-state listener uses for a Telegram Closed, so the same `AccountSessionLost`/banner path runs). If that transition also fails (the DB is down), add it as suppressed and rethrow the original failure.
  - Not confirmed: leave the session open; it already reports its state again.
- Keep the behaviour symmetric on the fake (`FakeTelegram`) and the real adapter. Update the comment above `deleteOrClose` and the `unlink` KDoc.

## RED first

IT (`UnlinkIT`, fake), two cases, with `deletion.delete` throwing once (the existing spy):
  1. Confirmed sign-out: the unlink throws; then (await) the row state is `session_lost` and the session is not open. Tighten the existing predicate at `UnlinkIT.kt:246` (`state == "session_lost" || !fake.isOpen(session)`), which passes on the false state, to assert `session_lost`. A retried unlink then succeeds and deletes the row.
  2. Unconfirmed sign-out (Telegram unreachable in the fake): the unlink throws; the session is still open and the row is still `connected` or `reconnecting` (not closed under it). A retried unlink succeeds.
Both are red today: case 1 leaves `connected`, case 2 closes the session.

## Definition of Done

If deletion.delete throws after a confirmed sign-out, the account is Session lost (its session closed) rather than connected/reconnecting with no client; after an unconfirmed sign-out the session is not closed and keeps reporting its state; UnlinkIT asserts the row state the Owner sees, not only the adapter's isOpen. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
