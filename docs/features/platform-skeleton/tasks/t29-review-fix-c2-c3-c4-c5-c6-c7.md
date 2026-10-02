---
id: T29
title: "Bring the auth and signed-in pages in line with screens.md and the accessibility rules"
layer: "ui"
deps: ["T28"]
acs: ["AC-83", "AC-92", "AC-100"]
files_hint: ["frontend/src/app/layouts.tsx", "frontend/src/pages/", "frontend/src/components/", "frontend/src/messages.ts", "frontend/src/styles"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 (C2, C3, C4, C5, C6, C7)"
status: "todo"
---

# T29 — Bring the auth and signed-in pages in line with screens.md and the accessibility rules

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows C2, C3, C4, C5, C6, C7 in [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-83, AC-92, AC-100 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Vitest: auth layout shows the logo asset; the resent alert has the info-circle icon; SCR-07 has a visible 'Sign-in code' label; SCR-10 matches screens.md (no extra heading, or screens.md updated); invalid inputs get aria-invalid + aria-describedby and errors sit in a live region; ConfirmDialog focuses its first button, traps focus and returns it on close. PageFrame icon buttons are ≥44 px on phone (CSS, checked in e2e).

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI.
- Commit with `SDD-Task: T29` and one `SDD-AC:` trailer per AC.
