---
id: T30
title: "List the new field codes in the contract and cap fixture condition codes at 63 characters"
layer: "ports"
deps: []
acs: ["AC-178"]
files_hint: ["docs/features/app-shell/contracts/openapi.yaml", "backend/app/src/main/kotlin/telex/web/e2e/PulseFixtures.kt", "backend/app/src/integrationTest/kotlin/telex/web/PulseApiIT.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03-2 (R2-8, R2-9)"
status: "todo"
---

# T30 — List the new field codes in the contract and cap fixture condition codes at 63 characters

Follow-up from the second independent review. The findings, with `file:line` citations and suggested fixes, are rows R2-8, R2-9 in [`_review/review-2026-10-03-2.md`](../_review/review-2026-10-03-2.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-178 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

openapi.yaml FieldProblem.code lists unknown-property (and the PATCH /me/preferences 400 description gives an example), and the fixture's ValidationFailed lists pattern; PulseFixtures refuses a code longer than 63 characters with 400 validation-failed (PulseApiIT case for a 64-character code).

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew :backend:app:test :backend:app:integrationTest --tests 'telex.web.PulseApiIT' detekt spotlessCheck`.
- Commit with `SDD-Task: T30` and one `SDD-AC:` trailer per AC.
