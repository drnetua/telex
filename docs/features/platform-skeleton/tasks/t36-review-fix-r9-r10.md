---
id: T36
title: "Make the rollback check see column length and the failed-notice assertion exact"
layer: "tests"
deps: ["T35"]
acs: ["AC-98"]
files_hint: ["backend/app/src/integrationTest/kotlin/telex/MigrationRollbackIT.kt", "backend/app/src/integrationTest/kotlin/telex/identity/NewSignInNoticeIT.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 re-review (R9, R10)"
status: "todo"
---

# T36 — Make the rollback check see column length and the failed-notice assertion exact

Follow-up from the re-review. The findings, with `file:line` citations and suggested fixes, are rows R9, R10 in the "Re-review" section of [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-98 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

MigrationRollbackIT's column snapshot includes character_maximum_length (proved by temporarily breaking U202610021600 locally, not committed); NewSignInNoticeIT awaits listeners after the first sign-in and asserts exactly 1 incomplete publication.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI (`e2e/` is type-checked by `./gradlew :e2e:check`).
- Commit with `SDD-Task: T36` and one `SDD-AC:` trailer per AC.
