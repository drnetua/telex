---
id: T39
title: "Sign in again racing an unlink never reports success on a deleted account or leaves an authorized session behind"
layer: "app"
deps: []
blocks: ["T41", "T45"]
acs: ["AC-111", "AC-117", "AC-04"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/internal/account/LinkedAccountRows.kt", "backend/app/src/main/kotlin/telex/messaging/internal/account/LinkCompletion.kt", "backend/app/src/main/kotlin/telex/messaging/internal/account/AccountDeletion.kt", "backend/app/src/main/kotlin/telex/messaging/LinkedAccounts.kt", "backend/app/src/main/kotlin/telex/messaging/Linking.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkIT.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingCompletionIT.kt"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-04 — findings N1, T1"
status: "todo"
---

# T39 — Sign in again racing an unlink never reports success on a deleted account or leaves an authorized session behind

## Origin

Follow-up from the independent re-review: [`_review/review-2026-10-04.md`](../_review/review-2026-10-04.md), findings **N1, T1** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-111, AC-117, AC-04
- **Blocked by:** — · **Blocks:** T41, T45

## What to change

- `LinkedAccountRows.swapSession` ignores its update count, so `LinkCompletion.signInAgain` publishes `LinkedAccountStateChanged(CONNECTED)` and returns `SignedInAgain` for an account an unlink deleted in the meantime. The new authorized session is then referenced by no row and is never logged out: a teleX device stays in the user's Telegram. An untargeted Add account that `LinkRules.sameOwner` turns into a sign-in-again is not covered by `discardTargeting`, and `AccountDeletion.delete` does not take the `linked_account:<owner>` advisory lock.
- Make the swap detect the vanished row (return the update count, or lock the row with `SELECT … FOR UPDATE` in `complete`) and treat 0 rows as a refusal, so the attempt is discarded and its session logged out — never a success. And/or take the same Owner advisory lock in `AccountDeletion.delete` so the two serialize. Keep ADR-0002 unlink ordering.
- Correct the comment at `LinkedAccounts.kt` ("the session read below is final") so it is true.
- `UnlinkIT` "a session swapped in meanwhile is not left behind" never calls `unlink`; make it drive `LinkedAccounts.unlink` with the swap injected between the read and the delete (e.g. a `@MockitoSpyBean` on `LinkedAccountRows`), and assert the replacement is logged out, closed and its directory gone — so deleting the `session != signedOut` branch turns it red.

## RED first

IT: complete a Sign in again for account X after an unlink of X committed (inject the ordering deterministically); assert no `SignedInAgain`, no `CONNECTED` event for X, the new session logged out + closed + directory gone. IT: the strengthened UnlinkIT above. Prove each test red against the current code before fixing.

## Definition of Done

The race leaves nothing behind and never reports success; the UnlinkIT race test fails if the replacement-session branch is removed. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
