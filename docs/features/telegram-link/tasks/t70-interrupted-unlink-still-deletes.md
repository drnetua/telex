---
id: T70
title: "An unlink whose sign-out is interrupted still deletes the account and puts the interrupt back only at the end"
layer: "app"
deps: []
blocks: ["T72"]
acs: ["AC-113", "AC-111"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/LinkedAccounts.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkIT.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (seventh pass) — finding K14"
status: "done"
---

# T70 — An unlink whose sign-out is interrupted still deletes the account and puts the interrupt back only at the end

## Origin

Follow-up from the seventh-pass review: [`_review/review-2026-10-04-r7.md`](../_review/review-2026-10-04-r7.md), finding **K14** (resolved "Fix now" by the user). Read that row in the review record first — it carries the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-113, AC-111
- **Blocked by:** — · **Blocks:** T72

## What to change

- K14: `LinkedAccounts.signOut` (`:167-169`) catches `InterruptedException` and calls `Thread.currentThread().interrupt()` at once. `deleteOrClose` then runs the JDBC delete on that thread. Request threads are virtual, and socket I/O on an interrupted virtual thread throws `SocketException: Closed by interrupt`, so the delete fails: a 500, and the account is not deleted.
- Fix: `signOut` treats the interrupt as not confirmed and records it without setting the flag. `unlink` puts the flag back in a `finally` after the delete, close and destroy. Keep the unlink KDoc true.

## RED first

Integration (`UnlinkIT`): run `accounts.unlink` on a virtual thread with a sign-out that throws `InterruptedException`. Assert the call returns `signOutConfirmed == false`, the `linked_account` row is gone, and the thread's interrupt flag is set on return. Today it fails with `TransactionSystemException` and the row stays.

## Definition of Done

An unlink whose sign-out is interrupted deletes the account, answers `signOutConfirmed=false`, and leaves the thread's interrupt flag set when it returns. Per-task gate clean (unit + integration + detekt/ktlint). No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
