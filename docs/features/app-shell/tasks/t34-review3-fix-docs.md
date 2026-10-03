---
id: T34
title: "Bring the docs in line after review 3: SCR-10 loading N/A, proxy wording, settled risks, applied-theme wording, gap 5 due date, both-projects check deviation"
layer: "docs"
deps: ["T33"]
acs: ["AC-170", "AC-176", "AC-179"]
files_hint: ["docs/features/app-shell/screens.md", "docs/features/app-shell/sad.md", "docs/features/app-shell/adr/0004-detect-offline-from-pulse-failures-and-browser-network-state.md", "docs/features/app-shell/test-plan.md"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03-3 (R3-1, R3-4, R3-5)"
status: "todo"
---

# T34 — Bring the docs in line after review 3: SCR-10 loading N/A, proxy wording, settled risks, applied-theme wording, gap 5 due date, both-projects check deviation

Follow-up from the third independent review. The findings, with `file:line` citations and suggested fixes, are rows R3-1, R3-4, R3-5 in [`_review/review-2026-10-03-3.md`](../_review/review-2026-10-03-3.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-170, AC-176, AC-179 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

screens.md SCR-10 loading row reads N/A (AppLayout gates on getMe; the shell W-S0 starting state covers it; AC-173), with the InboxPage branch kept as a guard; sad.md §4/§6/§8 and ADR-0004 drop 'from the proxy' for 502/503/504 and screens.md noted gap 3 reads done; the sad.md §11 risk row no longer lists the phone-bar and banner-order questions as open; screens.md W-S6 and SCR-64 theme-default say the theme applied on this device is checked; noted gap 5 gets a due date; test-plan.md (and sad.md QG-3b) record the missing both-projects CI comparison as a deliberate deviation, as R2-12 did.

- RED first (failing test quoted), then GREEN, then the per-task gate: none (docs only).
- Commit with `SDD-Task: T34` and one `SDD-AC:` trailer per AC.
