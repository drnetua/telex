---
id: T22
title: "Make the theme switches keyboard-correct: one radio group per switch and a real menu keyboard pattern"
layer: "ui"
deps: ["T21"]
acs: ["AC-43", "AC-179"]
files_hint: ["frontend/src/components/ThemeSwitch/ThemeSwitch.tsx", "frontend/src/components/ThemeSwitch/ThemeSwitch.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03 (C2, C6)"
status: "todo"
---

# T22 — Make the theme switches keyboard-correct: one radio group per switch and a real menu keyboard pattern

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows C2, C6 in [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-43, AC-179 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Vitest: two segmented ThemeSwitch instances rendered together have distinct radio group names (useId), and ArrowRight on the last radio of one never focuses or checks a radio of the other; the menu variant focuses the checked menuitemradio on open, ArrowUp/ArrowDown/Home/End move focus, Escape closes and returns focus to the trigger.

- RED first (failing test quoted), then GREEN, then the per-task gate: `pnpm run check` in `frontend/`.
- Commit with `SDD-Task: T22` and one `SDD-AC:` trailer per AC.
