---
id: T32
title: "Keep focus inside ConfirmDialog while busy and land it on a stable element after removal; ship a right-sized logo"
layer: "ui"
deps: ["T31"]
acs: ["AC-92"]
files_hint: ["frontend/src/components/ConfirmDialog/", "frontend/src/pages/profile-security/PasskeysCard.tsx", "frontend/src/pages/profile-security/ProfileSecurityPage.test.tsx", "frontend/src/assets/", "frontend/src/app/layouts.tsx", "frontend/src/app/layouts.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 re-review (R6, R7)"
status: "done"
---

# T32 — Keep focus inside ConfirmDialog while busy and land it on a stable element after removal; ship a right-sized logo

Follow-up from the re-review. The findings, with `file:line` citations and suggested fixes, are rows R6, R7 in the "Re-review" section of [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-92 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Vitest: while the confirm button is busy, focus is on the dialog (not body) and Tab stays inside; after a successful passkey removal focus lands on a stable element of the Passkeys card (not body); the auth layout uses a derived ~192 px logo (uncropped, same colours per the Logos README) well under 50 KB, the canonical file stays untouched.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI (`e2e/` is type-checked by `./gradlew :e2e:check`).
- Commit with `SDD-Task: T32` and one `SDD-AC:` trailer per AC.
