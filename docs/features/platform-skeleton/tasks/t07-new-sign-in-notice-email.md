---
id: T7
title: "Send the \"New sign-in to teleX\" email from the SignInSessionStarted event after commit"
layer: "app"
deps: ["T3", "T4"]
blocks: ["T20"]
acs: ["AC-98"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/internal/email/", "backend/app/src/main/resources/application.yaml", "backend/app/src/integrationTest/kotlin/telex/identity/"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T7 — Send the "New sign-in to teleX" email from the SignInSessionStarted event after commit

## Place in the sequence

- **Blocked by:** T3 — Create the mail integration module with the Mailer port and the SMTP adapter, T4 — Build the Owner and Sign-in Session core: start, resolve with the 30/90-day rules, end by key, and the SignInSessionStarted event · **Blocks:** T20 — Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator · **Wave:** 3 — needs the Mailer port (T3) and the event + session row (T4).
- **Lane:** shares `identity/internal/email/` with T5 (different files; parallel unless `implement` sees an overlap).

## Why (user story)

> **As an** Owner
> **I want** an email every time someone signs in to my account in a new session
> **So that** I notice a takeover through my mailbox before it does damage
>
> — `spec.md §4, US-47, verbatim` · full text: [spec.md](../spec.md)

This task delivers the email that tells the Owner about every later sign-in.

## Inlined context

> **"New sign-in to teleX":** sent asynchronously. `identity` publishes `SignInSessionStarted` (no secrets in the payload), and an `@ApplicationModuleListener` in `identity` renders the email and calls `Mailer` after commit. The event publication registry keeps the event until it completes, so a failed send is retried.
>
> — `adr/0004 §Decision outcome, abridged` · full text: [adr/0004](../adr/0004-send-email-through-a-new-mail-integration-module.md)

> Flow US-47: deliver after commit → check the publication is still incomplete → *already complete* skip → *the sign-in created the account* mark the publication complete, no email (AC-98) → *existing account* read the address the account was created with, and the session's browser, device type and time zone → send New sign-in to teleX with browser, device type, local time with the zone name, UTC and the sessions link → *send fails* leave the publication incomplete (retried by resubmitting incomplete publications on every app restart, no backoff timer in E01) → *delivered* mark complete.
>
> — `sad.md §6, Flow US-47, abridged` · full text: [sad.md](../sad.md)

> - **Behaviour:** `createdAccount = true` → complete without an email (AC-98); otherwise send "New sign-in to teleX" with browser, device type, local time with the zone name, UTC, and the sessions link (`TELEX_PUBLIC_URL` + SCR-64 route, ADR-0006).
> - **Retry:** no backoff timer in E01. Incomplete publications are resubmitted on every app restart (`spring.modulith.events.republish-outstanding-events-on-restart=true`).
> - **Validator:** the `@ApplicationModuleTest` scenario for Flow US-47 (`Scenario.publish(...)…andWaitForEventOfType`) pins the payload shape.
>
> — `contracts/events.md §Event + §Idempotency & retry + §Schema registry, abridged` · full text: [events.md](../contracts/events.md)

**Sessions link (route set by this breakdown, shared with T13/T19):** `PublicUrl.link("/profile", fragment = "sessions")` → `http://localhost:8080/profile#sessions` (SCR-64 sessions card anchor, screens.md SCR-64).

> **Time:** […] The new-sign-in email shows the signing-in browser's IANA zone (sent by the SPA at sign-in, stored on the session) with its name, and UTC (AC-98)
>
> — `sad.md §8, Time, abridged` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Column | Use | Change |
|---|---|---|
| `owner.email` | recipient (address as created) | read |
| `sign_in_session.user_agent_label`, `device_type`, `time_zone`, `started_at` | email body | read |
| `event_publication.completion_date` | set by Modulith on success; stays NULL on failure | read/write (framework) |

— `data-model.md §Entities + contracts/events.md, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no API surface. Consumes `telex.identity.SignInSessionStarted(ownerId, sessionId, createdAccount)`; calls `telex.mail.Mailer.send(OutgoingEmail(template = "new-sign-in", …))`.

## Acceptance criteria

### AC-98 — happy

> **Given** an Owner who already has an account
> **When** a new Sign-in Session starts for them by link, code or passkey
> **Then** they receive a "New sign-in to teleX" email naming the browser, device type and time (in the time zone of the browser that signed in, with the zone's name, and in UTC), with a link to the sessions list in Profile and security; no such email is sent for the sign-in that creates the account
>
> — `spec.md §5, AC-98, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Listener `NewSignInNotice` (`@ApplicationModuleListener`) in `identity/internal/email/`; returns early when `createdAccount`.
- [ ] Template: subject "New sign-in to teleX"; body with label (e.g. "Safari on iPhone"), device type in words, local time + zone id (e.g. "2 Oct 2026, 14:03 (Europe/Kyiv)"), the same instant in UTC, the sessions link, sentence case, no emoji.
- [ ] `spring.modulith.events.republish-outstanding-events-on-restart: true` in `application.yaml`.
- [ ] Scenario test under `backend/app/src/integrationTest/kotlin/telex/identity/` with `RecordingMailer` (T3).

## Edge cases

| Case | Behaviour |
|---|---|
| `createdAccount = true` | no email; publication completes (AC-98) |
| Owner signed in as `Anton+work@Mail.com`, account created as `anton@mail.com` | email goes to `anton@mail.com` (AC-34) |
| Session `time_zone = UTC` (browser sent none) | local time shown as UTC with zone name "UTC" |
| `RecordingMailer` set to fail | publication stays incomplete in `event_publication` |
| Passkey sign-in (`createdAccount = false`) | email sent — same path as link and code |

## Definition of Done

- [ ] Scenario test passes for every edge case.
- [ ] Email asserts: recipient, subject, both times, zone name, absolute sessions link built from `PublicUrl`.
- [ ] No address or session key in the event payload or logs.
- [ ] every Hard Rule inlined above still holds.
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green.
