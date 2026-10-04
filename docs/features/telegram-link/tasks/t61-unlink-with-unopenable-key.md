---
id: T61
title: "An unlink whose sealed key can't be opened still deletes the account and reports the sign-out unconfirmed"
layer: "infra"
deps: []
blocks: ["T62", "T65"]
acs: ["AC-113", "AC-111"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/LinkedAccounts.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkIT.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (fifth pass) — findings K9"
status: "done"
---

# T61 — An unlink whose sealed key can't be opened still deletes the account and reports the sign-out unconfirmed

## Origin

Follow-up from the fifth-pass review: [`_review/review-2026-10-04-r5.md`](../_review/review-2026-10-04-r5.md), findings **K9** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-113, AC-111
- **Blocked by:** — · **Blocks:** T62, T65

## What to change

- K9: `LinkedAccounts.reopenIfClosed` (`LinkedAccounts.kt:136`) calls `ownerKeys.open`, which ends in `AesGcm.open` (`identity/internal/key/AesGcm.kt:35`). That throws a checked `GeneralSecurityException` for a wrong key, a wrong AAD, tampering or a truncated value. `signOut` (`LinkedAccounts.kt:150-154`) catches only `RuntimeException`, so the exception escapes `unlink`, `ProblemHandler` answers 500 and the delete never runs. The accounts this hits are exactly the ones boot can't reopen (`BootReconnect.kt:68` catches `Exception` for this reason). Such an account is never open, so every unlink of it fails. Before T57 the same unlink returned "not confirmed" and deleted the account.
- Fix: in `signOut`, catch `Exception`, not only `RuntimeException` (the `TooGenericExceptionCaught` suppression is already there), or wrap `reopenIfClosed` the same way. Any failure to open the key or reach Telegram means "not confirmed".

## RED first

IT (`UnlinkIT`, fake): link an account, close its session in the fake (as if boot never reopened it), and overwrite `linked_account.sealed_key` with bytes that fail to decrypt (for example a truncated value, or a flipped byte). `DELETE` the account: assert 200, `signOutConfirmed=false`, and the row is gone. Today this is a 500 and the row survives.

## Definition of Done

An unlink of an account whose sealed key fails to decrypt (wrong key, tampered or truncated value) answers 200 with signOutConfirmed=false and deletes the account; no checked exception from the key path escapes unlink as a 500. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
