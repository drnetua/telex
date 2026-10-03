---
id: T36
title: "Bring the docs in line after review 4: 502/503/504 inside vs outside the shell, W-10b wireframe N/A"
layer: "docs"
deps: ["T35"]
acs: ["AC-170", "AC-176"]
files_hint: ["docs/features/app-shell/contracts/openapi.yaml", "docs/features/app-shell/sad.md", "docs/features/app-shell/adr/0004-detect-offline-from-pulse-failures-and-browser-network-state.md", "docs/features/app-shell/screens.md"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03-4 (R4-2, R4-3)"
status: "done"
---

# T36 — Bring the docs in line after review 4: 502/503/504 inside vs outside the shell, W-10b wireframe N/A

Follow-up from the fourth independent review. The findings, with `file:line` citations and suggested fixes, are rows R4-2, R4-3 in [`_review/review-2026-10-03-4.md`](../_review/review-2026-10-03-4.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-170, AC-176 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

openapi.yaml info (around line 38), sad.md §4 (line 110) and §8 (line 586) and ADR-0004 (line 29) say a 502/503/504 reads as 'teleX isn't responding' inside the shell and goes to SCR-93 outside it (e.g. the first GET /api/v1/me before the shell mounts), matching screens.md:47-50 and :500; screens.md W-10b wireframe block (lines 376-380) is labelled N/A (guard only, see the SCR-10 loading row) or removed.

- RED first (failing test quoted), then GREEN, then the per-task gate: none (docs only).
- Commit with `SDD-Task: T36` and one `SDD-AC:` trailer per AC.
