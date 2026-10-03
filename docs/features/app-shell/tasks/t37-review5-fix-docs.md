---
id: T37
title: "Bring the docs in line after review 5: no answer and network errors are 'not responding' only inside the shell"
layer: "docs"
deps: []
acs: ["AC-176"]
files_hint: ["docs/features/app-shell/adr/0004-detect-offline-from-pulse-failures-and-browser-network-state.md", "docs/features/app-shell/sad.md"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03-5 (R5-1)"
status: "todo"
---

# T37 — Bring the docs in line after review 5: no answer and network errors are 'not responding' only inside the shell

Follow-up from the fifth independent review. The finding, with `file:line` citations and the suggested fix, is row R5-1 in [`_review/review-2026-10-03-5.md`](../_review/review-2026-10-03-5.md) — read it first; it is this task's brief.

## Acceptance criteria touched

AC-176 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

ADR-0004 (considered option 1), sad.md §4 decision 3 and the sad.md file-tree note for api/client.ts scope the whole no-answer / network-error / 502/503/504 list to inside the shell and say that outside the shell (before it mounts) each of them still opens SCR-93, matching frontend/src/api/client.ts routeFor, sad.md §8 failure routing and screens.md:47-53.

- RED first (failing test quoted), then GREEN, then the per-task gate: none (docs only).
- Commit with `SDD-Task: T37` and one `SDD-AC:` trailer per AC.
