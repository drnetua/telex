---
id: T24
title: "Make framework errors use contract problem codes and validate API bodies against openapi.yaml"
layer: "ports"
deps: ["T22"]
acs: ["AC-34", "AC-82", "AC-83", "AC-84", "AC-85", "AC-93", "AC-97", "AC-101"]
files_hint: ["backend/app/src/main/kotlin/telex/web/ProblemHandler.kt", "backend/app/src/test/kotlin/telex/web/ProblemHandlerTest.kt", "backend/app/src/integrationTest/kotlin/telex/web/", "gradle/libs.versions.toml", "backend/app/build.gradle.kts"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 (A8, B5)"
status: "todo"
---

# T24 — Make framework errors use contract problem codes and validate API bodies against openapi.yaml

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows A8, B5 in [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-34, AC-82, AC-83, AC-84, AC-85, AC-93, AC-97, AC-101 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Malformed/empty JSON on the sign-in POSTs answers 400 validation-failed; every emitted `code` is in the openapi ErrorCode enum. The shared *ApiIT request helpers validate success and problem bodies against contracts/openapi.yaml (e.g. swagger-request-validator / openapi4j), so drift fails a test.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI.
- Commit with `SDD-Task: T24` and one `SDD-AC:` trailer per AC.
