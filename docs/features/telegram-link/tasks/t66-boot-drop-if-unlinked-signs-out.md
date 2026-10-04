---
id: T66
title: "Boot signs out a session reopened for an account unlinked meanwhile, and a unit test pins it"
layer: "infra"
deps: []
blocks: ["T69"]
acs: ["AC-111"]
files_hint: ["backend/app/src/test/kotlin/telex/messaging/internal/lifecycle/BootReconnectTest.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (sixth pass) — findings K12"
status: "todo"
---

# T66 — Boot signs out a session reopened for an account unlinked meanwhile, and a unit test pins it

## Origin

Follow-up from the sixth-pass review: [`_review/review-2026-10-04-r6.md`](../_review/review-2026-10-04-r6.md), findings **K12** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-111
- **Blocked by:** — · **Blocks:** T69

## What to change

- K12: since T63 the fake reopens a destroyed directory signed out, so in no IT can `BootReconnect.dropIfUnlinked`'s `telegram.logOut` (`BootReconnect.kt:99`) return true, and the other boot-race test (`LifecycleIT.kt:218-228`) gets `wasLoggedOut == true` from the unlink's own reopen. Deleting line 99 leaves every test green. With real TDLib the line is still needed: the unlink's `awaitReopen` gives up after 10 s, deletes the row and destroys the directory while TDLib holds the database, and the reopen then registers an authorized session that only `dropIfUnlinked` can sign out.
- Fix: a `BootReconnect` unit test with Mockito mocks of `LinkedAccountRows`, `OwnerKeys`, `TelegramSessions`, `LinkedAccountStates`, `SessionStateListener`, `LifecycleMetrics`. No production change expected.

## RED first

Unit (`BootReconnectTest`): one account, `rows.findBySession` returns null after `reopen`. Run `run().get()` and assert, in order, `logOut(session, …)`, `close(session)`, `destroy(session)`. Plus: when `logOut` throws, `close` and `destroy` still run; when the account is still held, none of the three runs. The test passes against the current code; prove it pins the line by checking it goes red with line 99 removed (record the mutation in the commit message).

## Definition of Done

A unit test fails if BootReconnect stops signing out, closing or destroying (in that order) a session it reopened for an account that was unlinked meanwhile, and shows close and destroy still run when the sign-out throws. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
