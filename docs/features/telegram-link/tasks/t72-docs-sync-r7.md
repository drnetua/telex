---
id: T72
title: "Correct the AC-121 citations and the interrupted-unlink claims after the seventh review"
layer: "docs"
deps: ["T70", "T71"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/test-plan.md", "docs/features/telegram-link/tasks.json", "docs/features/telegram-link/tasks/tracker.md", "docs/features/telegram-link/tasks/t62-failed-delete-after-sign-out-state.md", "docs/features/telegram-link/tasks/t67-logout-interrupt-unmutes.md", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightLogOutTest.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (seventh pass) — findings D17, D18"
status: "done"
---

# T72 — Correct the AC-121 citations and the interrupted-unlink claims after the seventh review

## Origin

Follow-up from the seventh-pass review: [`_review/review-2026-10-04-r7.md`](../_review/review-2026-10-04-r7.md), findings **D17, D18** (resolved "Fix now" by the user).

- **ACs:** —
- **Blocked by:** T70, T71 · **Blocks:** —

## What to change

- D17: `test-plan.md:123` cites AC-121 (chat list sync) for "a delete fails after an unconfirmed sign-out → Reconnecting returns to Connected". Cite AC-122 and AC-113. Drop AC-121 from `TdlightLogOutTest.kt:149`, the T62 and T67 frontmatter and their `tasks.json` entries, where nothing about the chat list is asserted.
- D18: `test-plan.md:124`, `tracker.md:74` and the T67 title claim an interrupted sign-out can't fail the unlink, at unit level. Point the bullet at T70's `UnlinkIT` (integration), and keep the unit claim to what `TdlightLogOutTest` shows (closing on an interrupted thread disposes without throwing).
- Mark T70–T72 `done`; update the tracker total.

## RED first

Docs only, no test.

## Definition of Done

No telegram-link artifact cites AC-121 for an unlink behaviour; the test plan, tracker and T67 describe the interrupted unlink as T70's IT proves it. `tasks.json` matches every task file.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
