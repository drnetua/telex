---
id: T76
title: "Sync the data model, test plan and tracker with the eighth-review fixes"
layer: "docs"
deps: ["T73", "T74", "T75"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/data-model.md", "docs/features/telegram-link/test-plan.md", "docs/features/telegram-link/tasks.json", "docs/features/telegram-link/tasks/tracker.md"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (eighth pass) — K15, K16, K17 follow-through"
status: "todo"
---

# T76 — Sync the data model, test plan and tracker with the eighth-review fixes

## Origin

Follow-up from the eighth-pass review: [`_review/review-2026-10-04-r8.md`](../_review/review-2026-10-04-r8.md).

- **ACs:** —
- **Blocked by:** T73, T74, T75 · **Blocks:** —

## What to change

- `data-model.md`: the `phone_country_code` / `phone_last_digits` rows say they are refreshed on each sign-in, like `display_name` (K15).
- `test-plan.md`: the interrupted-unlink bullet covers an interrupt that doesn't arrive as `InterruptedException` (K16), and the close-and-destroy ordering is backed by T74's assertion (K17).
- Mark T73–T76 `done`; update the tracker total.

## RED first

Docs only, no test.

## Definition of Done

The data model, test plan and tracker describe what T73–T75 prove. `tasks.json` matches every task file.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
