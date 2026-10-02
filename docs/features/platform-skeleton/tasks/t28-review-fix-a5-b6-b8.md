---
id: T28
title: "Mark /me refetches as background and cover the untested SCR-64 and SCR-07 states"
layer: "ui"
deps: ["T27"]
acs: ["AC-96", "AC-89", "AC-91", "AC-97", "AC-100"]
files_hint: ["frontend/src/api/account.ts", "frontend/src/api/client.test.ts", "frontend/src/pages/profile-security/ProfileSecurityPage.test.tsx", "frontend/src/pages/check-email/CheckEmailPage.test.tsx", "frontend/src/pages/inbox/InboxPage.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 (A5, B6, B8)"
status: "todo"
---

# T28 — Mark /me refetches as background and cover the untested SCR-64 and SCR-07 states

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows A5, B6, B8 in [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-96, AC-89, AC-91, AC-97, AC-100 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Vitest: useMe refetches carry X-Telex-Background: 1; SCR-64 passkeys-unsupported, adding, add-cancelled, add-failed toast, added ('Never used'), removing, removed-on-404 and the #sessions anchor are asserted; SCR-07 400 code-format shows no tries left; the Inbox test really chooses twice.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI.
- Commit with `SDD-Task: T28` and one `SDD-AC:` trailer per AC.
