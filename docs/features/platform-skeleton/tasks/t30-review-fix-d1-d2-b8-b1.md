---
id: T30
title: "Make the e2e run reliable: app healthcheck, pinned and type-checked e2e, strict AC-82 assertion, removed-passkey e2e"
layer: "tests"
deps: ["T25"]
acs: ["AC-33", "AC-82", "AC-92"]
files_hint: ["compose.yaml", ".github/workflows/ci.yml", "e2e/", "build.gradle.kts", "settings.gradle.kts", "pnpm-workspace.yaml", "pnpm-lock.yaml"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 (D1, D2, B8, B1)"
status: "todo"
---

# T30 — Make the e2e run reliable: app healthcheck, pinned and type-checked e2e, strict AC-82 assertion, removed-passkey e2e

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows D1, D2, B8, B1 in [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-33, AC-82, AC-92 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

compose `app` has a healthcheck so `docker compose up --wait` waits for it; e2e deps pinned to caret ranges and `tsc --noEmit` over e2e runs in the build/CI; the AC-82 e2e asserts exactly 'This link was already used'; an e2e removes a passkey, signs out and sees passkey sign-in refused with email sign-in still offered.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI.
- Commit with `SDD-Task: T30` and one `SDD-AC:` trailer per AC.
