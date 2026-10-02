---
id: T26
title: "Prove session lifetime over HTTP and that raw secrets never reach logs or the event registry"
layer: "tests"
deps: ["T24"]
acs: ["AC-96"]
files_hint: ["backend/app/src/integrationTest/kotlin/telex/web/SessionsApiIT.kt", "backend/app/src/integrationTest/kotlin/telex/identity/"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 (B3, B4)"
status: "todo"
---

# T26 — Prove session lifetime over HTTP and that raw secrets never reach logs or the event registry

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows B3, B4 in [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-96 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

HTTP integration tests: an X-Telex-Background: 1 request leaves last_activity_at unchanged while an unmarked one moves it; a cookie idle 30+ days or older than 90 days gets 401 session-ended. One test runs request → redeem → sign-out with output capture and asserts the raw token, code and cookie appear in neither logs nor event_publication.serialized_event.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI.
- Commit with `SDD-Task: T26` and one `SDD-AC:` trailer per AC.
