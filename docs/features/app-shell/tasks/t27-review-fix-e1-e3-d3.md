---
id: T27
title: "Bring the docs in line: ux-flows platform decision, Coming soon copy and settled open questions, inventory line refs, raw-UUID deviation"
layer: "docs"
deps: ["T20", "T22", "T24", "T25"]
acs: ["AC-171", "AC-179", "AC-184", "AC-175"]
files_hint: ["docs/features/app-shell/ux-flows.md", "docs/features/app-shell/screens.md", "docs/features/app-shell/spec.md", "docs/design-system.md", "docs/features/app-shell/adr/"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03 (E1, E2, E3, D3)"
status: "done"
---

# T27 — Bring the docs in line: ux-flows platform decision, Coming soon copy and settled open questions, inventory line refs, raw-UUID deviation

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows E1, E2, E3, D3 in [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-171, AC-179, AC-184, AC-175 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

ux-flows.md §Platform decisions and Flow US-73 say the theme lives in the shell and the picker opens in a dialog (matching screens.md:605); screens.md Coming soon copy question and spec §8 phone-bar and banner-order questions are ticked with the settled answers; docs/design-system.md Icon/ThemeSwitch/StatusBanner rows point at the current file:line in frontend/src; feature ADR-0003 and ADR-0006 record the raw UUID owner id on InboxSource/StatusConditionSource as an accepted deviation from foundation ADR-0003 with the reason (OwnerId lives in identity).

- RED first (failing test quoted), then GREEN, then the per-task gate: docs-only: `./gradlew spotlessCheck` and `pnpm run check` in `frontend/` still clean.
- Commit with `SDD-Task: T27` and one `SDD-AC:` trailer per AC.
