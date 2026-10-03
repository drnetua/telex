---
id: T32
title: "Bring the docs in line after review 2: ADR-0006 deviation heading, draft labels, inventory line refs, applied-theme wording, deferred visual tier, load-test sample deviation"
layer: "docs"
deps: ["T29", "T30", "T31"]
acs: ["AC-179", "AC-181", "AC-171"]
files_hint: ["docs/features/app-shell/adr/0006-extend-the-shell-through-client-section-and-server-condition-registries.md", "docs/features/app-shell/screens.md", "docs/design-system.md", "docs/features/app-shell/test-plan.md"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03-2 (R2-10, R2-11, R2-12)"
status: "done"
---

# T32 — Bring the docs in line after review 2: ADR-0006 deviation heading, draft labels, inventory line refs, applied-theme wording, deferred visual tier, load-test sample deviation

Follow-up from the second independent review. The findings, with `file:line` citations and suggested fixes, are rows R2-10, R2-11, R2-12 in [`_review/review-2026-10-03-2.md`](../_review/review-2026-10-03-2.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-179, AC-181, AC-171 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

ADR-0006 deviation heading sits after the last Positive bullet, just before Negative; screens.md:531 drops 'draft' and gaps 2 and 6 read as done; docs/design-system.md Toast and FailureBoundary rows point at the current lines; screens.md:82 says the menu marks the theme applied on this device; test-plan.md marks the visual-regression level and its CI line deferred to spec §8; test-plan.md §NFR validation records 5 in-page samples per PR as a deliberate deviation from 20 on a schedule.

- RED first (failing test quoted), then GREEN, then the per-task gate: none (docs only).
- Commit with `SDD-Task: T32` and one `SDD-AC:` trailer per AC.
