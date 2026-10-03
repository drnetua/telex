---
id: T33
title: "Keep a superseded theme save off SCR-93's Retry"
layer: "ui"
deps: []
acs: ["AC-181", "AC-182"]
files_hint: ["frontend/src/api/preferences.ts", "frontend/src/components/ThemeSwitch/ThemeSwitch.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03-3 (R3-2)"
status: "done"
---

# T33 — Keep a superseded theme save off SCR-93's Retry

Follow-up from the third independent review. The findings, with `file:line` citations and suggested fixes, are rows R3-2 in [`_review/review-2026-10-03-3.md`](../_review/review-2026-10-03-3.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-181, AC-182 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

When a failed theme save is superseded (currentChoice() no longer equals the failed choice), a sign-in or session-ended failure still routes, but a server failure (unavailable) does not hand SCR-93 a retry that re-applies the old choice (pass a no-op retry or skip routing for unavailable). Vitest: another tab picks System via a storage event, the Dark save answers 500, Try again on SCR-93 runs, and telex.theme and the applied theme stay System; the existing non-superseded 500 path still routes with a working retry.

- RED first (failing test quoted), then GREEN, then the per-task gate: `pnpm run check` in `frontend/`.
- Commit with `SDD-Task: T33` and one `SDD-AC:` trailer per AC.
