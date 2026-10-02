---
id: T22
title: "Serialise Sign-in Grant issue per canonical address so at most one grant is live"
layer: "app"
deps: ["T21"]
acs: ["AC-103"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/SignIn.kt", "backend/app/src/main/kotlin/telex/identity/internal/grant/GrantRows.kt", "backend/app/src/integrationTest/kotlin/telex/identity/SignInIssueIT.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 (A4)"
status: "todo"
---

# T22 — Serialise Sign-in Grant issue per canonical address so at most one grant is live

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows A4 in [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-103 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Integration test: N parallel requests for one address leave exactly one live grant (the others superseded); implemented with pg_advisory_xact_lock(hashtext(canonical)) before supersedeLive.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI.
- Commit with `SDD-Task: T22` and one `SDD-AC:` trailer per AC.
