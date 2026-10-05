---
id: T81
title: "An unlink takes any pending interrupt just before the delete, including when there is no sign-out"
layer: "app"
deps: []
blocks: ["T82"]
acs: ["AC-113", "AC-111"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/LinkedAccounts.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkIT.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (ninth pass) — D22 (code part)"
status: "todo"
---

# T81 — An unlink takes any pending interrupt just before the delete, including when there is no sign-out

## Origin

Follow-up from the ninth-pass review: [`_review/review-2026-10-05.md`](../_review/review-2026-10-05.md), D22 (code part) (resolved "Fix now" by the user). Read those rows first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-113, AC-111
- **Blocked by:** — · **Blocks:** T82

## What to change

- T74 moves a pending interrupt into the flag at the end of `signOut` (`LinkedAccounts.kt:185`), not just before the delete as the task asked. When `signOut` is skipped (a Session lost account, or no `TelegramSessions` bean), nothing clears the flag before `deleteOrClose` (`:87`).
- Add `if (Thread.interrupted()) interrupted = true` right before `deleteOrClose`, so every path into the delete is covered. The existing `finally` hands it back.

## RED first

Integration (`UnlinkIT`): on a virtual thread, unlink a Session lost account with the thread already interrupted (or interrupted by the `rows` spy in `getMine`). Assert no failure, the rows gone and the flag set on return. Today it fails with `JDBC rollback failed`.

## Definition of Done

An unlink deletes the account when the thread is interrupted and the sign-out is skipped (a Session lost account), and the interrupt is handed back at the end. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
