---
id: T25
title: "Prove passkey sign-in end to end: removed passkey refused, success ends the held session and sends the notice"
layer: "tests"
deps: ["T23"]
acs: ["AC-92", "AC-104", "AC-98", "AC-89"]
files_hint: ["backend/app/src/integrationTest/kotlin/telex/web/PasskeyCeremoniesIT.kt", "backend/app/src/integrationTest/kotlin/telex/identity/NewSignInNoticeIT.kt", "backend/app/src/integrationTest/kotlin/telex/mail/RecordingMailer.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 (B1, B2, B7)"
status: "todo"
---

# T25 — Prove passkey sign-in end to end: removed passkey refused, success ends the held session and sends the notice

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows B1, B2, B7 in [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-92, AC-104, AC-98, AC-89 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Integration tests: after removing a real passkey its assertion is refused and no session starts; a successful passkey sign-in ends the session the browser held, starts a session of the same kind and records a 'New sign-in to teleX' notice; NewSignInNoticeIT also covers the code path; the failed-send test waits on a recorded attempt (latch/counter) instead of sleep.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI.
- Commit with `SDD-Task: T25` and one `SDD-AC:` trailer per AC.
