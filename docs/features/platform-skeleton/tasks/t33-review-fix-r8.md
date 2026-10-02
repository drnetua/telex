---
id: T33
title: "Prove the 44 px PageFrame touch targets at phone width in the browser"
layer: "tests"
deps: ["T32"]
acs: ["AC-33"]
files_hint: ["e2e/tests/", "e2e/support/"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 re-review (R8)"
status: "done"
---

# T33 — Prove the 44 px PageFrame touch targets at phone width in the browser

Follow-up from the re-review. The findings, with `file:line` citations and suggested fixes, are rows R8 in the "Re-review" section of [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-33 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

A Playwright test in the phone project asserts boundingBox width and height >= 44 for the PageFrame 'Profile and security' and 'Sign out' buttons; e2e `tsc --noEmit` is clean.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI (`e2e/` is type-checked by `./gradlew :e2e:check`).
- Commit with `SDD-Task: T33` and one `SDD-AC:` trailer per AC.
