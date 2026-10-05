---
id: T95
title: "Sync the test plan, sad, screens and task files with the twelfth-review fixes"
layer: "docs"
deps: ["T92", "T93", "T94"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/test-plan.md", "docs/features/telegram-link/sad.md", "docs/features/telegram-link/screens.md", "docs/features/telegram-link/tasks.json", "docs/features/telegram-link/tasks/tracker.md"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (twelfth pass) — W23, W24, H-T5"
status: "todo"
---

# T95 — Sync the test plan, sad, screens and task files with the twelfth-review fixes

## Origin

Follow-up from the twelfth-pass review: [`_review/review-2026-10-05-r4.md`](../_review/review-2026-10-05-r4.md), W23, W24, H-T5 (resolved "Fix now" by the user). Read that row first.

- **ACs:** —
- **Blocked by:** T92, T93, T94 · **Blocks:** —

## What to change

- `test-plan.md`: the backoff retry (AC-116/121), the guard cases and the Updating resume (AC-116/122, H-T5), the page-level wait announcement (AC-02).
- sad Flow 11: a failed load is retried after a backoff while connected, and again on the next connection Ready.
- screens.md SCR-02 `wait`: where the announcement lives, if it says.
- Mark T92–T95 done in the tracker and task files.

## RED first

Docs only: no test. Check by script that `tasks.json`, the task files and the tracker agree.

## Definition of Done

test-plan.md describes the backoff retry, the restart guards, the Updating resume and the page-level live region; sad Flow 11 and screens.md SCR-02 wait match; tasks.json matches every task file. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
