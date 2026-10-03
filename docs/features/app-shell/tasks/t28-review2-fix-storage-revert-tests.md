---
id: T28
title: "Guard the remembered destination against blocked storage, revert a failed theme save only over its own choice, and cover the review-1 test gaps"
layer: "ui"
deps: []
acs: ["AC-173", "AC-175", "AC-181", "AC-182"]
files_hint: ["frontend/src/api/destination.ts", "frontend/src/app/FailureBoundary.tsx", "frontend/src/app/FailureBoundary.test.tsx", "frontend/src/api/preferences.ts", "frontend/src/components/ThemeSwitch/ThemeSwitch.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03-2 (R2-1, R2-2, R2-5, R2-6)"
status: "done"
---

# T28 — Guard the remembered destination against blocked storage, revert a failed theme save only over its own choice, and cover the review-1 test gaps

Follow-up from the second independent review. The findings, with `file:line` citations and suggested fixes, are rows R2-1, R2-2, R2-5, R2-6 in [`_review/review-2026-10-03-2.md`](../_review/review-2026-10-03-2.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-173, AC-175, AC-181, AC-182 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

destination.ts reads and writes localStorage inside try/catch, and FailureBoundary clears the cache and navigates even when remembering throws (Vitest with a throwing localStorage: the cache is empty and /sign-in or /session-ended is reached); a failed theme save reverts only while currentChoice() still equals the failed choice, otherwise it just clears pending (Vitest: another tab picks System via a storage event, then the Dark save fails, and both stay on System with no revert written to storage); a Vitest where me.theme differs from the applied theme proves a failed save reverts to the applied theme (A4b); a Vitest proves the session-ended route clears the query cache.

- RED first (failing test quoted), then GREEN, then the per-task gate: `pnpm run check` in `frontend/`.
- Commit with `SDD-Task: T28` and one `SDD-AC:` trailer per AC.
