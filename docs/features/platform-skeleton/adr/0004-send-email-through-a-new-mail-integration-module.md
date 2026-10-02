---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-02"
feature_size: "M"
ticket: "E01 platform-skeleton"
---

# 0004 — Send email through a new `mail` integration module; sign-in emails synchronously, notices by event

- **Status:** Accepted
- **Date:** 2026-10-02
- **Deciders:** Anton Husiev, Claude (design Socratic walk)

## Context

teleX now sends two emails: the sign-in email (Sign-in Link + Sign-in Code) and "New sign-in to teleX" (AC-98). SMTP is an external system. The repo's convention is that external systems are reached only through integration modules (ACLs that depend on `shared` only), and none of the 13 modules covers email. Modulith event payloads are stored in `event_publication` in readable form, so a payload carrying a link or code would break spec §6.1.

## Decision drivers

- Spec §6.1: link and code never stored in readable form.
- Architecture-map convention: integration modules are the only place external systems are touched; `ApplicationModules.verify()` enforces boundaries.
- AC-98: the new-sign-in email must not be lost when the mail server hiccups.
- AC-33: the first sign-in works against the bundled local mailbox (Mailpit).

## Considered options

1. **A new 14th module `telex.mail` (integration ACL).** It holds a `Mailer` port with an `OutgoingEmail` value and an SMTP adapter on `spring-boot-starter-mail`, and depends on `shared` only. `identity` adds `mail` to its `allowedDependencies`.
2. **A mail port and SMTP adapter inside `identity/internal`.** No new module.

## Decision outcome

**Chosen:** option 1. The two emails take different routes:
- **Sign-in email:** sent synchronously through `Mailer` inside the request that issues the grant (ADR-0003). The plaintext link and code exist only in memory and in the email itself. If sending fails, the transaction rolls back, so no orphaned grant remains, and the person sees an error and can try again.
- **"New sign-in to teleX":** sent asynchronously. `identity` publishes `SignInSessionStarted` (no secrets in the payload), and an `@ApplicationModuleListener` in `identity` renders the email and calls `Mailer` after commit. The event publication registry keeps the event until it completes, so a failed send is retried.

`identity` owns the templates, and `mail` only delivers. Option 2 breaks the ACL convention, and the first other module that needs email (a notice or digest in a later epic) would force moving it anyway.

## Consequences

**Positive**
- The ACL rule holds, and swapping SMTP for a provider API later touches only `mail`.
- The secret-bearing email never touches durable storage. The notice email survives a mail-server outage.
- Tests replace `Mailer` with an in-memory fake that captures emails (link and code are read from the fake, not the database).

**Negative**
- Deviation from the tech spec's "13 modules". `docs/architecture-map.md` and the module list must be updated (sad §11).
- A slow mail server slows the "send me a link" request, since sending is synchronous.

**Neutral**
- Incomplete event publications are retried on restart (`spring.modulith.events.republish-outstanding-events-on-restart`); there is no in-process retry loop in E01.

## Links

- Spec: [[../spec.md]] AC-33, AC-34, AC-98, §6.1
- SAD: [[../sad.md]] §4
- Related ADR: [[0003-redeem-sign-in-link-and-code-as-one-hashed-single-use-grant]]
