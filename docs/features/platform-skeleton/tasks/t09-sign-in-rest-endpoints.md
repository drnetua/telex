---
id: T9
title: "Expose the sign-in REST endpoints: request email, preview and redeem link, redeem code, sign out"
layer: "ports"
deps: ["T5", "T6", "T8"]
blocks: ["T19"]
acs: ["AC-34", "AC-82", "AC-84", "AC-85", "AC-86", "AC-103", "AC-95"]
files_hint: ["backend/app/src/main/kotlin/telex/web/api/SignInController.kt", "backend/app/src/main/kotlin/telex/web/api/", "backend/app/src/integrationTest/kotlin/telex/web/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T9 — Expose the sign-in REST endpoints: request email, preview and redeem link, redeem code, sign out

## Place in the sequence

- **Blocked by:** T5 — Issue a Sign-in Grant with its email and read a Sign-in Link without redeeming it, T6 — Redeem a Sign-in Link or Sign-in Code atomically and start the Sign-in Session, T8 — Add the Spring Security filter chain with the session cookie, CSRF and no-store, and switch problem codes to kebab-case · **Blocks:** T19 — Make teleX start with one command: Dockerfile, compose with app and Mailpit, README and a smoke script · **Wave:** 4 — needs `SignIn` issue/preview (T5), redeem (T6) and the security chain + cookie writer (T8).
- **Lane:** own file; shares `web/api/` with T10 and T12 (different controllers — parallel unless `implement` serializes the directory).

## Why (user story)

> **As an** Owner
> **I want** to sign up and sign in with just my email address, using the link or the code from the email
> **So that** I never create or remember a password, on whichever device I read my email
>
> — `spec.md §4, US-01, verbatim` · full text: [spec.md](../spec.md)

This task puts the email sign-in on the wire: the SPA can ask for an email, show the confirm page, redeem a link or a code, and sign out.

## Inlined context

> - JSON property names are camelCase […]
> - Every state-changing request (POST, DELETE) — public ones included — carries the `X-XSRF-TOKEN` header […]
> - Every response carries `Cache-Control: no-store` (sad §8 Caching, AC-95).
> - Errors are RFC 9457 `application/problem+json`: `type = urn:telex:error:<code>`, the `code` extension and, for field errors, `errors[]`. `code` keys the SPA message catalog.
>
> — `contracts/openapi.yaml, info.description, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

> Other contract decisions […] The browser time zone is the `X-Telex-Time-Zone` header, because Spring's `/login/webauthn` body can't carry it. Sign-out is public and idempotent.
>
> — `contracts/api-sync-report.md §C, abridged` · full text: [api-sync-report.md](../contracts/api-sync-report.md)

> **Errors:** RFC 9457 `application/problem+json` with `type = urn:telex:error:<code>`, `code` and `errors[]`; domain errors extend `telex.shared.DomainProblem`, rendered by `telex.web.ProblemHandler`.
>
> — `CLAUDE.md §Layout and code conventions, Errors, verbatim` · full text: [CLAUDE.md](../../../../CLAUDE.md)

Uses: T5/T6 `SignIn` (`request`, `preview`, `redeemByLink`, `redeemByCode` — they throw the domain problems below), T4 `SignInSessions.endByKey`, T8 `SessionCookies` (set/clear `telex_session`). Pass `User-Agent`, `X-Telex-Time-Zone` and the held `telex_session` cookie into each redeem.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes (all persistence via `identity`).

## API contract

| operationId | Request | Success | Errors |
|---|---|---|---|
| `requestSignInEmail` `POST /api/v1/sign-in/grants` | `{email}` (EmailAddress ≤254) | `201 {grantId, email}` | `400 validation-failed` (`email`/`email-incomplete`), `403`, `503 mail-unavailable` |
| `previewSignInLink` `POST /api/v1/sign-in/link/preview` | `{linkToken}` (1–128) | `200 {email}` | `400`, `403`, `410` SignInGrantRefused |
| `redeemSignInLink` `POST /api/v1/sign-in/link/redeem` | `{linkToken}` + headers `X-Telex-Time-Zone`, cookie `telex_session` (held) | `200 {createdAccount}` + `Set-Cookie: telex_session=<key>; Max-Age=7776000; Path=/; HttpOnly; SameSite=Lax` (+`Secure`) | `400`, `403`, `410` |
| `redeemSignInCode` `POST /api/v1/sign-in/grants/{grantId}/code` | `{code}` `^[0-9]{6}$` + same headers/cookie | `200 {createdAccount}` + cookie | `400` (`code`/`code-format`, not counted), `403`, `410`, `422 sign-in-code-wrong` + `attemptsLeft` |
| `signOut` `POST /api/v1/sign-out` | cookie `telex_session` (optional) | `204` + `Set-Cookie: telex_session=; Max-Age=0; …` | `403` |

`410` SignInGrantRefused: `sign-in-link-expired` | `sign-in-link-used` | `sign-in-grant-void`, with `email` whenever the grant exists (absent for an unknown token or grant id).

— `contracts/openapi.yaml, operationIds requestSignInEmail / previewSignInLink / redeemSignInLink / redeemSignInCode / signOut + responses SignedIn, SignInGrantRefused, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-34 — happy

> **Given** a person whose email address has no teleX account
> **When** they enter the address on the sign-in page, open the Sign-in Link from the email and confirm "Continue as <address>"
> **Then** teleX creates their Owner account, signs them in in that browser, offers to create a Passkey, and afterwards shows the empty Inbox with the single step "Connect Telegram"; signing in again with the same address opens the same account, never a second one. Two addresses are the same when they match after ignoring letter case and any "+tag" before the @ (so `Anton+work@Mail.com` and `anton@mail.com` are one account). A sign-in email goes to the address exactly as typed that time; the "New sign-in to teleX" email goes to the address the account was created with
>
> — `spec.md §5, AC-34, verbatim` · full text: [spec.md](../spec.md)

### AC-82 — happy

> **Given** a person who asked for a sign-in email on their laptop and reads the email on their phone
> **When** they type the Sign-in Code from the email on the laptop's "Check your email" page
> **Then** the laptop is signed in, and the Sign-in Link from the same email no longer works
>
> — `spec.md §5, AC-82, verbatim` · full text: [spec.md](../spec.md)

### AC-84 — domain invariant

> **Given** a Sign-in Link or Sign-in Code that has already been used to sign in
> **When** anyone opens that link or types that code again
> **Then** sign-in is refused with "This link was already used", and a "Send a new link" action is offered
>
> — `spec.md §5, AC-84, verbatim` · full text: [spec.md](../spec.md)

### AC-85 — domain invariant

> **Given** a sign-in email whose Sign-in Code has been typed wrong 5 times
> **When** the person tries a 6th code, or opens the link from that email
> **Then** sign-in is refused, the person is told the code is no longer valid, and they are asked to request a new email
>
> — `spec.md §5, AC-85, verbatim` · full text: [spec.md](../spec.md)

### AC-86 — domain invariant

> **Given** a Sign-in Link opened by a mail scanner or a link preview that never confirms
> **When** the person later opens the same link and confirms "Continue as <address>" within 15 minutes
> **Then** they are signed in normally, because a link signs in only after the confirm step
>
> — `spec.md §5, AC-86, verbatim` · full text: [spec.md](../spec.md)

### AC-103 — domain invariant

> **Given** a person who asked for a sign-in email and then asked for another one for the same address
> **When** they open the link or type the code from the earlier email
> **Then** sign-in is refused as expired, with a "Send a new link" action; only the newest email for an address works, and a code typed on a "Check your email" page counts only against the email that this page asked for
>
> — `spec.md §5, AC-103, verbatim` · full text: [spec.md](../spec.md)

### AC-95 — happy

> **Given** a signed-in Owner
> **When** they choose "Sign out"
> **Then** they land on the sign-in page, and going back in the browser does not show any of their data
>
> — `spec.md §5, AC-95, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `web/api/SignInController.kt` with request DTOs (`@Valid`, `@Pattern`, `@Size`) matching the schemas, `additionalProperties: false` → reject unknown fields if Jackson is configured so; otherwise ignore.
- [ ] Map `SignedIn(key, createdAccount)` → `SessionCookies.set(response, key)` + `{createdAccount}`.
- [ ] `signOut`: `SignInSessions.endByKey(cookie)` + `SessionCookies.clear` — always `204`.
- [ ] `SignInApiIT` (MockMvc + `csrf()`, `RecordingMailer`, fixed `Clock`) — one test per row of the table and per edge case.

## Edge cases

| Case | Behaviour |
|---|---|
| Code `"12345"` or `"abcdef"` | `400 code-format`; `wrong_attempts` unchanged |
| `grantId` not a UUID | `400 validation-failed` |
| Sign-out with no cookie, or a session already ended | `204`, cookie cleared |
| Redeem while holding another Owner's cookie | `200`, new cookie, old session ended (AC-104 via T6) |
| Link preview, then confirm, by a mail scanner that only previews | preview changes nothing; a later confirm signs in (AC-86) |
| Back after sign-out | next API call has no cookie → `401 unauthenticated`; responses were `no-store` (AC-95) |

## Definition of Done

- [ ] `SignInApiIT` passes; responses validate against `contracts/openapi.yaml` schemas (assert fields and codes explicitly).
- [ ] The response bodies never contain the link token, the code or the session key.
- [ ] every Hard Rule inlined above still holds.
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green.
