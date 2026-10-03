---
id: T21
title: "Keep one shared theme state: applied choice, save sequence, pending, revert and the failed-save toast, and route 401/5xx preference-save failures"
layer: "ui"
deps: []
acs: ["AC-179", "AC-180", "AC-181", "AC-182", "AC-173", "AC-176"]
files_hint: ["frontend/src/api/preferences.ts", "frontend/src/shell/theme.ts", "frontend/src/shell/theme.test.ts", "frontend/src/components/ThemeSwitch/ThemeSwitch.tsx", "frontend/src/components/ThemeSwitch/ThemeSwitch.test.tsx", "frontend/src/app/layouts.tsx", "frontend/src/pages/profile-security/ProfileSecurityPage.test.tsx"]
owner: "Anton Husiev"
estimate: "M"
source: "review-2026-10-03 (A4, A6, C8)"
status: "todo"
---

# T21 — Keep one shared theme state: applied choice, save sequence, pending, revert and the failed-save toast, and route 401/5xx preference-save failures

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows A4, A6, C8 in [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-179, AC-180, AC-181, AC-182, AC-173, AC-176 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Vitest: (a) after a storage event from another tab the switch's checked option matches data-bs-theme; (b) after a cross-device me refetch the switch still shows the applied device theme (AC-181 does not switch an used device); (c) with two ThemeSwitch instances mounted, choosing Dark in one and System in the other before Dark answers, a failed Dark save does not revert over System, and every switch marks the pending choice as checked; (d) a save that fails after the originating switch unmounted still reverts and shows 'Your theme wasn't saved.' with Try again from a component that stays mounted; (e) with localStorage throwing, an explicit Light/Dark account choice is not overridden by a device mode change; (f) a theme or time zone save answered 401 unauthenticated/session-ended or 5xx reverts and then hands the failure to failureBus (sign-in / SCR-92 / SCR-93) with no toast, while 400, 403 and no-answer keep the toast; ProfileSecurityPage.test uses a network error for the 'no answer' case.

- RED first (failing test quoted), then GREEN, then the per-task gate: `pnpm run check` in `frontend/`.
- Commit with `SDD-Task: T21` and one `SDD-AC:` trailer per AC.
