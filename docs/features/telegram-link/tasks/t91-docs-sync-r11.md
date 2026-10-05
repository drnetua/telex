---
id: T91
title: "Sync the test plan, sad and task files with the eleventh-review fixes"
layer: "docs"
deps: ["T87", "T88", "T89", "T90"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/test-plan.md", "docs/features/telegram-link/sad.md", "docs/features/telegram-link/tasks.json", "docs/features/telegram-link/tasks/tracker.md"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (eleventh pass) — H-D24"
status: "todo"
---

# T91 — Sync the test plan, sad and task files with the eleventh-review fixes

## Origin

Follow-up from the eleventh-pass review: [`_review/review-2026-10-05-r3.md`](../_review/review-2026-10-05-r3.md), H-D24 (resolved "Fix now" by the user). Read that row first.

- **ACs:** —
- **Blocked by:** T87, T88, T89, T90 · **Blocks:** —

## What to change

- H-D24: `test-plan.md:61,131` say "This linking attempt ended"; use the screens.md copy, "This linking has ended".
- `test-plan.md`: the chat-load retry, Updating as connected, the empty-then-filled announcement and the Kathmandu pin.
- sad Flow 11 / the connection-state mapping, if T87 or T88 changed what it says.
- Mark T87–T91 done in the tracker and task files.

## RED first

Docs only: no test. Check by script that `tasks.json`, the task files and the tracker agree.

## Definition of Done

test-plan.md uses the screens.md ended-card copy and describes T87–T90; sad matches the reload and Updating behaviour; tasks.json matches every task file. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
