---
id: T24
title: "Fix More sheet focus (no re-steal, trapped Tab), announce the first Status Banner, and keep sticky toasts off the phone bar"
layer: "ui"
deps: ["T21"]
acs: ["AC-43", "AC-176"]
files_hint: ["frontend/src/shell/AppShell/AppShell.tsx", "frontend/src/shell/AppShell/AppShell.test.tsx", "frontend/src/shell/StatusBanner/StatusBanner.tsx", "frontend/src/shell/StatusBanner/StatusBanner.test.tsx", "frontend/src/components/Toast/Toast.tsx", "frontend/src/styles.css"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03 (C1, C3, C5, C7)"
status: "todo"
---

# T24 — Fix More sheet focus (no re-steal, trapped Tab), announce the first Status Banner, and keep sticky toasts off the phone bar

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows C1, C3, C5, C7 in [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-43, AC-176 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Vitest: with the More sheet open and focus on an item inside it, a re-render of AppShell (me cache update, pulse count change) leaves focus on that item; Tab and Shift+Tab stay inside the sheet (trap or inert background); StatusBanner renders an always-present empty role=status region and puts the first condition text into it; below 768 px the toast container is offset above the bottom bar (CSS rule present and asserted via class/style), using tokens only.

- RED first (failing test quoted), then GREEN, then the per-task gate: `pnpm run check` in `frontend/`.
- Commit with `SDD-Task: T24` and one `SDD-AC:` trailer per AC.
