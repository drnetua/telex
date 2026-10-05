---
id: T74
title: "An unlink deletes the account whatever way an interrupt arrives, and closes and destroys before handing it back"
layer: "app"
deps: []
blocks: ["T76"]
acs: ["AC-113", "AC-111"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/LinkedAccounts.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkIT.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (eighth pass) — findings K16, K17"
status: "done"
---

# T74 — An unlink deletes the account whatever way an interrupt arrives, and closes and destroys before handing it back

## Origin

Follow-up from the eighth-pass review: [`_review/review-2026-10-04-r8.md`](../_review/review-2026-10-04-r8.md), findings **K16, K17** (resolved "Fix now" by the user). Read those rows first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-113, AC-111
- **Blocked by:** — · **Blocks:** T76

## What to change

- K16: `LinkedAccounts.signOut`'s generic catch (`:177`) swallows a failure caused by an interrupt that never arrived as `InterruptedException`, such as one during `reopenIfClosed`'s JDBC read. The flag stays set, so the delete fails as in K14. Fix: just before the delete, move any pending interrupt into `interrupted` (`if (Thread.interrupted()) interrupted = true`). The existing `finally` hands it back.
- K17: the fake's `close` and `destroy` ignore interrupts, so nothing proves the interrupt is handed back only after them. The fake records whether either ran on an interrupted thread (test hook), and the interrupt IT asserts neither did.

## RED first

Integration (`UnlinkIT`):
- K16: on a virtual thread, unlink an account whose session boot hasn't reopened (`simulateStop`). The `rows` spy interrupts the thread inside `sealedKey`. Assert no failure, `signOutConfirmed == false`, the rows are gone and the flag is set on return. Today it fails with `JDBC rollback failed`.
- K17: in the existing interrupted-sign-out case, assert the fake's close and destroy did not run on an interrupted thread. Check it red by moving the restore before the close.

## Deviation (recorded 2026-10-05, ninth review D22)

The code took the pending interrupt at the end of `signOut` (`if (Thread.interrupted()) onInterrupt()`), not just before the delete. That covers both sign-outs, but not an unlink that skips the sign-out (a Session lost account, or no `TelegramSessions` bean). T81 adds the check just before `deleteOrClose` as well, so every path into the delete is covered.

## Definition of Done

An unlink deletes the account however an interrupt reaches it during the sign-out, and the session is closed and destroyed before the interrupt is handed back. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
