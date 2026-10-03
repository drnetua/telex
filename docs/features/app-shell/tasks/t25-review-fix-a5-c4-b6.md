---
id: T25
title: "Build the Time zone card's tz-not-yet state, return focus after a pick, and replace the tautological me test"
layer: "ui"
deps: ["T21"]
acs: ["AC-183", "AC-184", "AC-181"]
files_hint: ["frontend/src/pages/profile-security/TimeZoneCard.tsx", "frontend/src/components/TimeZonePicker/TimeZonePicker.tsx", "frontend/src/pages/profile-security/ProfileSecurityPage.test.tsx", "frontend/src/components/TimeZonePicker/TimeZonePicker.test.tsx", "frontend/src/api/account.test.tsx", "docs/features/app-shell/tasks/t15-time-zone-card-and-picker.md"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03 (A5, C4, B6)"
status: "done"
---

# T25 — Build the Time zone card's tz-not-yet state, return focus after a pick, and replace the tautological me test

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows A5, C4, B6 in [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-183, AC-184, AC-181 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Vitest: with me.timeZone null the Time zone card renders LoadState rows=1 (no 'UTC' text, no Change button) per screens.md tz-not-yet; after picking a zone, focus lands on the 'Change time zone' button once the save settles (not body); account.test asserts useMe parses theme, timeZone and timeZoneIsFallback from a stubbed /api/v1/me response instead of a literal; t15 task file lists tz-not-yet.

- RED first (failing test quoted), then GREEN, then the per-task gate: `pnpm run check` in `frontend/`.
- Commit with `SDD-Task: T25` and one `SDD-AC:` trailer per AC.
