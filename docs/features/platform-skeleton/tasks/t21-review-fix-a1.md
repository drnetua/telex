---
id: T21
title: "Reject addresses the mail library can't parse and roll back the grant on any send failure"
layer: "app"
deps: []
acs: ["AC-83", "AC-103"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/internal/owner/EmailAddress.kt", "backend/app/src/main/kotlin/telex/mail/internal/SmtpMailer.kt", "backend/app/src/main/kotlin/telex/identity/SignIn.kt", "backend/app/src/test/kotlin/telex/identity/internal/owner/EmailAddressTest.kt", "backend/app/src/test/kotlin/telex/mail/internal/SmtpMailerTest.kt", "backend/app/src/integrationTest/kotlin/telex/identity/SignInIssueIT.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 (A1)"
status: "todo"
---

# T21 — Reject addresses the mail library can't parse and roll back the grant on any send failure

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows A1 in [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-83, AC-103 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

Unit test: EmailAddress.parse refuses `a,b@c.de` and `x(y@z.co` (400 email-incomplete/validation-failed). SmtpMailer maps jakarta.mail.MessagingException to MailUnavailable. Integration test: a send that throws a checked MessagingException stores no grant and does not supersede the earlier live grant; the API answers 503 per openapi, never 500.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI.
- Commit with `SDD-Task: T21` and one `SDD-AC:` trailer per AC.
