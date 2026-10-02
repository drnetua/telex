---
id: T3
title: "Create the mail integration module with the Mailer port and the SMTP adapter"
layer: "wiring"
deps: []
blocks: ["T5", "T7"]
acs: ["AC-33"]
files_hint: ["backend/app/src/main/kotlin/telex/mail/", "backend/app/src/main/kotlin/telex/identity/package-info.java", "backend/app/build.gradle.kts", "gradle/libs.versions.toml", "backend/app/src/main/resources/application.yaml", "backend/app/src/main/resources/application-local.yaml", "backend/app/src/test/kotlin/telex/mail/", "backend/app/src/integrationTest/kotlin/telex/mail/"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T3 — Create the mail integration module with the Mailer port and the SMTP adapter

## Place in the sequence

- **Blocked by:** — · **Blocks:** T5 — Issue a Sign-in Grant with its email and read a Sign-in Link without redeeming it, T7 — Send the "New sign-in to teleX" email from the SignInSessionStarted event after commit · **Wave:** 1 — needs nothing upstream; starts in parallel with T1, T2 and T13.
- **Lane:** own lane (the only task touching `identity/package-info.java`).

## Why (user story)

> **As an** Operator
> **I want** to start a complete teleX installation with one command from the README
> **So that** the installation needs no manual setup before the first person can sign in
>
> — `spec.md §4, US-41, verbatim` · full text: [spec.md](../spec.md)

This task gives teleX its way to send email, so the first sign-in email lands in the local mailbox with no setup.

## Inlined context

> **Chosen:** option 1. The two emails take different routes:
> - **Sign-in email:** sent synchronously through `Mailer` inside the request that issues the grant (ADR-0003). […] If sending fails, the transaction rolls back, so no orphaned grant remains, and the person sees an error and can try again.
> - **"New sign-in to teleX":** sent asynchronously. `identity` publishes `SignInSessionStarted` […] and an `@ApplicationModuleListener` in `identity` renders the email and calls `Mailer` after commit.
>
> `identity` owns the templates, and `mail` only delivers.
>
> — `adr/0004 §Decision outcome, abridged` · full text: [adr/0004](../adr/0004-send-email-through-a-new-mail-integration-module.md)

> **`mail`** (new, integration) owns SMTP and depends on `shared` only (ADR-0004). `identity`'s `allowedDependencies` gains `mail`. `web`'s stay as they are: `web` never reaches `mail`.
> ```
> ├── mail/                          integration ACL (new module, depends on shared only)
> │   ├── Mailer, OutgoingEmail      port
> │   └── internal/SmtpMailer        spring-boot-starter-mail adapter
> ```
>
> — `sad.md §5, building blocks, abridged` · full text: [sad.md](../sad.md)

> `mailpit`, the local mailbox: SMTP on 1025 for the app, a web page on `http://localhost:8025` for the Operator. […] The Operator sets […] real SMTP settings (`TELEX_MAIL_*` → `spring.mail.*`) […]
> Metrics: […] `telex.mail.sent{template,outcome}`. No email addresses or secrets in tags.
>
> — `sad.md §7, Topology + Monitoring, abridged` · full text: [sad.md](../sad.md)

> **Hard rule (Logging):** **Never logged:** email addresses, link tokens, codes, session keys, WebAuthn payloads.
>
> — `sad.md §8, Logging, abridged` · full text: [sad.md](../sad.md)

> Module packages: integration → `shared` only. […] `telex.shared` is an OPEN kernel (typed ids, problems) with no Spring beans.
>
> — `CLAUDE.md §Layout and code conventions, abridged` · full text: [CLAUDE.md](../../../../CLAUDE.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — no API surface. Kotlin port at the `telex.mail` root:

- `data class OutgoingEmail(val to: String, val subject: String, val text: String, val template: String)` — `template` is a tag for the metric only.
- `interface Mailer { fun send(email: OutgoingEmail) }` — throws `MailUnavailable` (a `mail`-root exception) when the server refuses or times out, so the caller's transaction rolls back (ADR-0004).

## Acceptance criteria

### AC-33 — happy

> **Given** a clean machine that has only Docker and a copy of the repository
> **When** the Operator runs the one command from the README
> **Then** within 5 minutes (not counting the first build of the teleX application) the address named in the README, opened by the Operator in their browser, shows the sign-in page; the README names a local mailbox page where every email teleX sends shows up, and the Operator can then complete a first sign-in with the email from that mailbox (the sign-in itself is outside the 5 minutes)
>
> — `spec.md §5, AC-33, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `backend/app/src/main/kotlin/telex/mail/package-info.java` — `@ApplicationModule(displayName = "Mail", allowedDependencies = {"shared"})`, with a one-line Javadoc like the other modules.
- [ ] `Mailer`, `OutgoingEmail`, `MailUnavailable` at `telex.mail`; `internal/SmtpMailer` on `JavaMailSender`, counting `telex.mail.sent{template,outcome}`.
- [ ] Add `spring-boot-starter-mail` to `gradle/libs.versions.toml` (Boot-managed, no version) and `backend/app/build.gradle.kts`.
- [ ] `application.yaml`: `spring.mail.host/port/username/password` from `TELEX_MAIL_HOST` (default `localhost`), `TELEX_MAIL_PORT` (default `1025`), …; connection + read timeouts ≤ 5 s so a slow server fails before the SPA's 10 s timeout; a sender address (`TELEX_MAIL_FROM`, default `teleX <no-reply@localhost>`).
- [ ] Add `"mail"` to `allowedDependencies` in `backend/app/src/main/kotlin/telex/identity/package-info.java`.
- [ ] Integration-test double `RecordingMailer` (`@TestConfiguration`, `@Primary`) under `backend/app/src/integrationTest/kotlin/telex/mail/` that captures sent emails and can be told to fail.
- [ ] Unit test `SmtpMailerTest` with a mocked `JavaMailSender` (success + failure → `MailUnavailable`).

## Edge cases

| Case | Behaviour |
|---|---|
| SMTP server down or slower than the timeout | `MailUnavailable` thrown; counter `outcome=failed`; the address is not logged |
| `web` tries to import `telex.mail` | `ModularityTest` fails — `web` never reaches `mail` |
| Mailpit not running in `local` profile | app still boots; only sending fails |

## Definition of Done

- [ ] `SmtpMailerTest` passes; `RecordingMailer` is usable by later ITs.
- [ ] `ModularityTest` (`ApplicationModules.verify()`) green with 14 modules.
- [ ] No email address or body text in any log line or metric tag.
- [ ] every Hard Rule inlined above still holds.
- [ ] `./gradlew spotlessCheck detekt` clean.
