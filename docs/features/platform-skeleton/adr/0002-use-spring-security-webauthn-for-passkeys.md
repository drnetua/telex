---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead", "Security Lead"]
updated_at: "2026-10-02"
feature_size: "M"
ticket: "E01 platform-skeleton"
---

# 0002 — Use Spring Security's built-in WebAuthn support for Passkeys

- **Status:** Accepted
- **Date:** 2026-10-02
- **Deciders:** Anton Husiev, Claude (design Socratic walk)

## Context

Passkeys are offered after the account-creating sign-in (AC-89, AC-90, AC-105, AC-91). They sign an Owner in without typing an email, using a discoverable credential (AC-89). They are listed with a name, a creation date and a last-used date (AC-89) and can be removed, including the last one (AC-92). The tech spec names "Spring Security: passkeys (WebAuthn)". WebAuthn verification (attestation and assertion parsing, signature checks, counters) is security-critical code that we should not write ourselves.

## Decision drivers

- AC-89 (automatic name, creation and last-used dates, passkey sign-in without an email), AC-92, AC-97.
- Spec §6: sign-up, passkey creation and passkey sign-in work in current Chrome, Safari (macOS) and Safari (iOS).
- Tech spec §Tech stack: "Spring Security: passkeys (WebAuthn) + email magic link".
- Keep hand-written cryptographic verification at zero.

## Considered options

1. **Spring Security 7 `webAuthn()`** with its JDBC repositories (`JdbcPublicKeyCredentialUserEntityRepository`, `JdbcUserCredentialRepository`; tables `user_entities` and `user_credentials`, which already carry `label`, `created` and `last_used`) and its registration/authentication endpoints.
2. **webauthn4j-core directly inside `identity`**, with our own tables keyed by `owner_id`, our own `/api/passkeys/...` ceremony endpoints and our own challenge storage.

## Decision outcome

**Chosen:** option 1. It reuses the framework's verified ceremonies, and the stored columns already match AC-89. We fit it into our conventions at the edges:
- The WebAuthn user entity's `name` is the `OwnerId`, so a Passkey belongs to exactly one Owner.
- Listing and removing Passkeys go through our own `identity` API, which filters by that user entity (AC-97).
- A successful passkey sign-in is handed to `SignInSessions.start(...)` (ADR-0001) by an authentication success handler. That keeps one session mechanism and triggers the new-sign-in email (AC-98).
- The automatic name ("Safari on iPhone") is computed by our User-Agent mapper and passed as the credential label.

Option 2 matches our conventions more closely, but it means about 400 more lines of ceremony code and challenge handling. That is more surface for subtle security bugs, and it buys only path and column naming.

## Consequences

**Positive**
- Attestation and assertion verification come from a maintained framework, upgraded with the Boot BOM.
- Passkey sign-in plugs into the same filter chain as the rest of authentication.

**Negative**
- Spring owns two table shapes (`user_entities`, `user_credentials`). Our migration creates them to Spring's schema, and they have no `owner_id` column, so ownership goes through the user entity.
- The endpoints follow Spring's paths (`/webauthn/register/options`, `/webauthn/register`, `/webauthn/authenticate/options`, `/login/webauthn`) rather than `/api/**`. `SpaHosting` must not swallow them, and their errors are mapped to RFC 9457 by our handlers.
- A Passkey's key is its WebAuthn credential id (bytes, Base64URL in the API), not a UUIDv7. This is a documented exception to the ID convention.

**Neutral**
- Moving to option 2 later is possible without Owners re-registering: the credential ids and public keys can be copied into new tables.

## Links

- Spec: [[../spec.md]] AC-89, AC-90, AC-91, AC-92, AC-97, AC-105, §6
- SAD: [[../sad.md]] §4
- Related ADR: [[0001-keep-sign-in-sessions-in-an-identity-owned-table-behind-an-opaque-cookie]], [[0006-derive-links-cookie-security-and-passkey-rp-id-from-one-public-url]]
