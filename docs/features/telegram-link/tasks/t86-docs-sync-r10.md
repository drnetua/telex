---
id: T86
title: "Sync the test plan and task files with the tenth-review fixes, and file T78 under tests"
layer: "docs"
deps: ["T83", "T84", "T85"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/test-plan.md", "docs/features/telegram-link/tasks.json", "docs/features/telegram-link/tasks/tracker.md", "docs/features/telegram-link/tasks/t78-unlink-connected-e2e.md"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (tenth pass) — D23"
status: "todo"
---

# T86 — Sync the test plan and task files with the tenth-review fixes, and file T78 under tests

## Origin

Follow-up from the tenth-pass review: [`_review/review-2026-10-05-r2.md`](../_review/review-2026-10-05-r2.md), D23 (resolved "Fix now" by the user). Read that row first.

- **ACs:** —
- **Blocked by:** T83, T84, T85 · **Blocks:** —

## What to change

- D23: set T78's layer to `tests` in `tasks.json`, `t78-unlink-connected-e2e.md` and `tracker.md`.
- `test-plan.md`: the wait announcement in the Owner's zone when `me` arrives late, the "Checking the code" and "Checking the password" busy states, the UTC pin and the malformed `Retry-After` case.
- Mark T83–T86 done in the tracker and task files.

## RED first

Docs only: no test. Check by script that `tasks.json`, the task files and the tracker agree.

## Definition of Done

T78 is a `tests` task everywhere; test-plan.md describes T83–T85; tasks.json matches every task file. Per-task gate clean.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
