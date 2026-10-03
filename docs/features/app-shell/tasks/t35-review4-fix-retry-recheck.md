---
id: T35
title: "Re-check at retry time that a failed theme save hasn't been replaced by another tab"
layer: "ui"
deps: []
acs: ["AC-181", "AC-182"]
files_hint: ["frontend/src/api/preferences.ts", "frontend/src/components/ThemeSwitch/ThemeSwitch.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03-4 (R4-1)"
status: "todo"
---

# T35 — Re-check at retry time that a failed theme save hasn't been replaced by another tab

Follow-up from the fourth independent review. The findings, with `file:line` citations and suggested fixes, are rows R4-1 in [`_review/review-2026-10-03-4.md`](../_review/review-2026-10-03-4.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-181, AC-182 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

The SCR-93 retry handed out by a failed, non-superseded theme save re-checks at retry time: it re-runs chooseTheme only while the theme applied on this device still equals the one the failure reverted to (now.settled); if another tab has chosen since, it resolves without applying or remembering anything, so SCR-93 closes. Vitest: (1) Dark answers 500 while not superseded, SCR-93 gets a retry, another tab then picks System via a storage event, the retry runs, and telex.theme and the applied theme stay System; (2) a non-superseded 500's retry re-applies and re-sends Dark (the T33 DoD 'working retry').

- RED first (failing test quoted), then GREEN, then the per-task gate: `pnpm run check` in `frontend/`.
- Commit with `SDD-Task: T35` and one `SDD-AC:` trailer per AC.
