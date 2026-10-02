---
id: T31
title: "Keep the saved Retry across background failures and count page opens as activity"
layer: "ui"
deps: []
acs: ["AC-102", "AC-96"]
files_hint: ["frontend/src/app/FailureBoundary.tsx", "frontend/src/app/FailureBoundary.test.tsx", "frontend/src/app/queryClient.ts", "frontend/src/api/account.ts", "frontend/src/api/account.test.tsx", "frontend/src/api/client.ts"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 re-review (R1, R2)"
status: "todo"
---

# T31 — Keep the saved Retry across background failures and count page opens as activity

Follow-up from the re-review. The findings, with `file:line` citations and suggested fixes, are rows R1, R2 in the "Re-review" section of [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-102, AC-96 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Vitest: a mutation 503 shows SCR-93, then a focus refetch of /me fails, then Retry still sends the original mutation and clears SCR-93; a background failure during a successful Retry does not show 'Still no answer.'; a refetch on remount/page open sends no X-Telex-Background header while a window-focus or reconnect refetch sends it.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI (`e2e/` is type-checked by `./gradlew :e2e:check`).
- Commit with `SDD-Task: T31` and one `SDD-AC:` trailer per AC.
