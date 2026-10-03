---
id: T29
title: "Close the theme menu on Tab and focus-out, with a roving tabindex on its items"
layer: "ui"
deps: ["T28"]
acs: ["AC-179"]
files_hint: ["frontend/src/components/ThemeSwitch/ThemeSwitch.tsx", "frontend/src/components/ThemeSwitch/ThemeSwitch.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03-2 (R2-7)"
status: "done"
---

# T29 — Close the theme menu on Tab and focus-out, with a roving tabindex on its items

Follow-up from the second independent review. The findings, with `file:line` citations and suggested fixes, are rows R2-7 in [`_review/review-2026-10-03-2.md`](../_review/review-2026-10-03-2.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-179 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

menuitemradio buttons have tabIndex=-1; Tab or Shift+Tab from an item closes the menu, and so does focus leaving the menu root; Vitest proves Tab from an item closes the menu and the items are not tab stops.

- RED first (failing test quoted), then GREEN, then the per-task gate: `pnpm run check` in `frontend/`.
- Commit with `SDD-Task: T29` and one `SDD-AC:` trailer per AC.
