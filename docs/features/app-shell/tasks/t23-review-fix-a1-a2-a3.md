---
id: T23
title: "Remember the section on Session ended, clear the previous Owner's cache on sign-in, and keep a pulse 5xx on the banner"
layer: "ui"
deps: []
acs: ["AC-173", "AC-175", "AC-176"]
files_hint: ["frontend/src/app/FailureBoundary.tsx", "frontend/src/app/FailureBoundary.test.tsx", "frontend/src/shell/pulse.ts", "frontend/src/shell/pulse.test.tsx", "frontend/src/app/queryClient.ts", "frontend/src/shell/connectivity.ts", "docs/features/app-shell/sad.md"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03 (A1, A2, A3)"
status: "done"
---

# T23 — Remember the section on Session ended, clear the previous Owner's cache on sign-in, and keep a pulse 5xx on the banner

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows A1, A2, A3 in [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-173, AC-175, AC-176 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Vitest: a session-ended failure on /runs remembers /runs (first refusal still wins) before navigating to /session-ended; a sign-in-route failure clears the query cache (no cached me or pulse survives to the next sign-in); a pulse answered 500 reports not-responding to connectivity and keeps the screen (no SCR-93), while a mutation 500 still opens SCR-93; sad.md §8 'a pulse failure routes the same way' is amended to say a pulse 5xx is shown as not responding.

- RED first (failing test quoted), then GREEN, then the per-task gate: `pnpm run check` in `frontend/`.
- Commit with `SDD-Task: T23` and one `SDD-AC:` trailer per AC.
