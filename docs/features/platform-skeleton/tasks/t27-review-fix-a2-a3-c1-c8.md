---
id: T27
title: "Keep page actions correct across the Unavailable page: Retry carries its result, SCR-08 shows unusable, bare layout"
layer: "ui"
deps: []
acs: ["AC-102", "AC-103"]
files_hint: ["frontend/src/app/", "frontend/src/pages/sign-in/", "frontend/src/pages/check-email/", "frontend/src/pages/confirm-link/", "frontend/src/pages/create-passkey/", "frontend/src/pages/profile-security/PasskeysCard.tsx", "frontend/src/pages/system/"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 (A2, A3, C1, C8)"
status: "todo"
---

# T27 — Keep page actions correct across the Unavailable page: Retry carries its result, SCR-08 shows unusable, bare layout

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows A2, A3, C1, C8 in [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-102, AC-103 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Vitest: 503 on Send → Retry lands on SCR-07 with the new grant; 503 on 'Send a new link' → Retry → a code redeems against the new grant; a repeat failure shows 'Still no answer.' and keeps SCR-93; confirm-time 410 without email or 400 shows SCR-08 link-unusable; SCR-93 renders inside the bare system layout; ConfirmLinkPage no longer routes failures during render.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI.
- Commit with `SDD-Task: T27` and one `SDD-AC:` trailer per AC.
