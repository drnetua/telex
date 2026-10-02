---
id: T11
title: "Wire Spring Security WebAuthn for passkey registration and passkey sign-in into the one session mechanism"
layer: "wiring"
deps: ["T8"]
blocks: ["T12"]
acs: ["AC-89", "AC-105", "AC-92", "AC-104"]
files_hint: ["backend/app/src/main/kotlin/telex/web/security/", "backend/app/src/main/kotlin/telex/identity/internal/passkey/", "backend/app/build.gradle.kts", "gradle/libs.versions.toml", "backend/app/src/main/resources/application-local.yaml", "backend/app/src/integrationTest/kotlin/telex/web/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T11 — Wire Spring Security WebAuthn for passkey registration and passkey sign-in into the one session mechanism

## Place in the sequence

- **Blocked by:** T8 — Add the Spring Security filter chain with the session cookie, CSRF and no-store, and switch problem codes to kebab-case · **Blocks:** T12 — List and remove my Passkeys, filtered by my WebAuthn user entity · **Wave:** 4 — needs the filter chain and cookie writer (T8), which already depend on the session core (T4).
- **Lane:** shares `web/security/` with T8 (serialized after it) and `identity/internal/passkey/` with T12 (T12 depends on this task).

## Why (user story)

> **As an** Owner
> **I want** to create a Passkey after my first sign-in (or skip it) and use it to sign in later
> **So that** signing back in takes one touch instead of a trip to my mailbox
>
> — `spec.md §4, US-45, verbatim` · full text: [spec.md](../spec.md)

This task lets an Owner create a passkey and later sign in with one touch, without teleX writing any WebAuthn cryptography of its own.

## Inlined context

> **Chosen:** option 1. It reuses the framework's verified ceremonies, and the stored columns already match AC-89. We fit it into our conventions at the edges:
> - The WebAuthn user entity's `name` is the `OwnerId`, so a Passkey belongs to exactly one Owner.
> - Listing and removing Passkeys go through our own `identity` API, which filters by that user entity (AC-97).
> - A successful passkey sign-in is handed to `SignInSessions.start(...)` (ADR-0001) by an authentication success handler. That keeps one session mechanism and triggers the new-sign-in email (AC-98).
> - The automatic name ("Safari on iPhone") is computed by our User-Agent mapper and passed as the credential label.
>
> — `adr/0002 §Decision outcome, verbatim` · full text: [adr/0002](../adr/0002-use-spring-security-webauthn-for-passkeys.md)

> **Chosen:** option 1 […] the WebAuthn RP ID is its host and the allowed origin is its origin. […] Developing through the Vite dev server (`pnpm dev`, port 5173) needs that origin in the allowed WebAuthn origins and Vite proxies for `/webauthn/**` and `/login/webauthn`. This is set in the `local` profile only.
>
> — `adr/0006 §Decision outcome + §Consequences, abridged` · full text: [adr/0006](../adr/0006-derive-links-cookie-security-and-passkey-rp-id-from-one-public-url.md)

> *WebAuthn challenge* — kept in a short-lived `HttpSession` for the ceremony only (Spring's default options repositories); no table (user decision, 2026-10-02). Sign-in Sessions themselves stay on `sign_in_session` (ADR-0001).
> `created` / `last_used` are `TIMESTAMP WITH TIME ZONE` instead of `timestamp` […] **Verify** with an integration test that saves and reloads a credential (`implement`).
>
> — `data-model.md §Not stored + §Deviations from Spring's DDL, abridged` · full text: [data-model.md](../data-model.md)

> Flow US-45 sign in with a passkey: verified → resolve the Owner from the credential's user entity → update the credential's last-used date and signature counter → end the session this browser held, insert the new session (AC-104) → record the session-started event → set the session cookie. *Unknown or removed credential, or the signature does not verify* → refusal → SCR-01 says the passkey did not work (AC-92).
> Flow US-45 passkey step: register the credential → verify the challenge and the origin, label it after the browser and device → *verification fails* → refusal (AC-105) → *verified* → insert the credential with its label, creation date and no last-used date.
>
> — `sad.md §6, Flows US-45, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** Passkeys come from the framework, not from us. […] No hand-written cryptographic verification.
>
> — `sad.md §4, strategic choice 3, abridged` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Table.column | Use | Change |
|---|---|---|
| `user_entities.id` / `name` (= `OwnerId` text, `user_entities_name_uq`) / `display_name` (= `owner.email`) | find-or-create at registration options; Owner lookup at sign-in | read/write |
| `user_credentials.*` | insert at register (`label` from UA mapper, `created` = now, `last_used` NULL); update `last_used`, `signature_count` at sign-in | read/write |
| `sign_in_session` | via `SignInSessions.start` | write |

— `data-model.md §Aggregate: Passkey, abridged` · full text: [data-model.md](../data-model.md)

## API contract

| operationId | Auth | Success | Errors |
|---|---|---|---|
| `passkeyRegistrationOptions` `POST /webauthn/register/options` | session | `200` PublicKeyCredentialCreationOptions (`rp.id` = PublicUrl host, `user.name` = OwnerId, `user.displayName` = owner.email, `residentKey: required`) | `401`, `403` |
| `registerPasskey` `POST /webauthn/register` | session | `200 {success: true}`; request `label` ignored | `400 passkey-registration-failed`, `401`, `403` |
| `passkeyAuthenticationOptions` `POST /webauthn/authenticate/options` | public | `200` request options, `allowCredentials: []` | `403` |
| `signInWithPasskey` `POST /login/webauthn` | public | `200 {createdAccount: false}` + `telex_session` cookie | `401 passkey-rejected`, `403` |

— `contracts/openapi.yaml, operationIds passkeyRegistrationOptions / registerPasskey / passkeyAuthenticationOptions / signInWithPasskey, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-89 — happy

> **Given** an Owner on a passkey-capable browser who has just signed in for the first time
> **When** they accept "Create a passkey" and confirm with their device
> **Then** the passkey is listed in Profile and security, named automatically after its browser and device (for example "Safari on iPhone"), with its creation date and its last-used date ("Never used" until first use), and their next sign-in on that device completes with "Sign in with a passkey" on the sign-in page and the device check alone, without typing an email address
>
> — `spec.md §5, AC-89, verbatim` · full text: [spec.md](../spec.md)

### AC-105 — error

> **Given** an Owner on a passkey-capable browser on the passkey step after their first sign-in
> **When** they cancel the device check, or creating the passkey fails
> **Then** they stay on the passkey step, see that no passkey was created, and can try again or choose "Not now"; no passkey is added to the list
>
> — `spec.md §5, AC-105, verbatim` · full text: [spec.md](../spec.md)

### AC-92 — happy

> **Given** an Owner with one or more Passkeys, including their only one
> **When** they remove a passkey in Profile and security and confirm
> **Then** the passkey disappears from the list and can no longer sign them in, and sign-in by email keeps working; Sign-in Sessions already open on any device stay open, and the confirm step reminds the Owner to end a lost device's session in the sessions list (AC-93)
>
> — `spec.md §5, AC-92, verbatim` · full text: [spec.md](../spec.md)

### AC-104 — domain invariant

> **Given** a browser that is already signed in, as the same Owner or as another one
> **When** a Sign-in Link is confirmed or a Sign-in Code is typed in that browser
> **Then** the existing Sign-in Session in that browser ends and a new one starts for the Owner the email was sent to, with the "New sign-in to teleX" email per AC-98
>
> — `spec.md §5, AC-104, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Add `spring-security-webauthn` + `webauthn4j-core` (Boot BOM) to `gradle/libs.versions.toml` and `backend/app/build.gradle.kts`.
- [ ] `identity/internal/passkey/`: `JdbcPublicKeyCredentialUserEntityRepository` + `JdbcUserCredentialRepository` beans; a public identity API for `web` to resolve `OwnerId` ↔ user entity and to label a new credential (keep `web` off identity internals).
- [ ] `web/security/`: `http.webAuthn { rpId(publicUrl.host); allowedOrigins(publicUrl.origin [+ http://localhost:5173 in local]); rpName("teleX") }`.
- [ ] Override the label on registration with the T2 User-Agent mapper.
- [ ] Success handler → `SignInSessions.start(ownerId, heldKey, userAgent, X-Telex-Time-Zone, createdAccount = false)` + `SessionCookies.set` + JSON `{createdAccount:false}`; counter `telex.signin.redeemed{method=passkey}`.
- [ ] Failure handlers → problem JSON `passkey-rejected` (401) / `passkey-registration-failed` (400).
- [ ] `application-local.yaml`: the Vite origin; extend the frontend Vite proxy in T13.
- [ ] `PasskeyCeremoniesIT`, including the `timestamptz` save/reload check.

## Edge cases

| Case | Behaviour |
|---|---|
| Registration options while signed out | `401 unauthenticated` |
| Second passkey for the same Owner | same user entity, second credential row |
| Assertion for a credential removed in T12 | `401 passkey-rejected`; email sign-in unaffected (AC-92) |
| Passkey sign-in while holding another Owner's session | old session ended, new one for the passkey's Owner (AC-104) |
| Origin not equal to `PublicUrl.origin` (outside `local`) | `400 passkey-registration-failed` / `401 passkey-rejected` |
| Challenge `HttpSession` | used for the ceremony only; never read by the session repository |

## Definition of Done

- [ ] `PasskeyCeremoniesIT` passes for every edge case.
- [ ] No hand-written signature or attestation verification in the diff.
- [ ] `created`/`last_used` round-trip through `timestamptz` unchanged (data-model deviation 1 verified).
- [ ] every Hard Rule inlined above still holds.
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green.
