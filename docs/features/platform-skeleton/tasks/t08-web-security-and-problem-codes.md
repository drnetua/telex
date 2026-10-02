---
id: T8
title: "Add the Spring Security filter chain with the session cookie, CSRF and no-store, and switch problem codes to kebab-case"
layer: "wiring"
deps: ["T4"]
blocks: ["T9", "T10", "T11"]
acs: ["AC-96", "AC-101", "AC-95", "AC-83"]
files_hint: ["backend/app/src/main/kotlin/telex/web/security/", "backend/app/src/main/kotlin/telex/web/ProblemHandler.kt", "backend/app/src/main/kotlin/telex/web/SpaHosting.kt", "backend/app/src/test/kotlin/telex/web/ProblemHandlerTest.kt", "backend/app/src/integrationTest/kotlin/telex/web/", "backend/app/build.gradle.kts", "gradle/libs.versions.toml", "backend/app/src/main/resources/application.yaml", "CLAUDE.md", "docs/architecture-map.md"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T8 — Add the Spring Security filter chain with the session cookie, CSRF and no-store, and switch problem codes to kebab-case

## Place in the sequence

- **Blocked by:** T4 — Build the Owner and Sign-in Session core: start, resolve with the 30/90-day rules, end by key, and the SignInSessionStarted event · **Blocks:** T9 — Expose the sign-in REST endpoints: request email, preview and redeem link, redeem code, sign out, T10 — List and end my Sign-in Sessions and answer "who am I", Owner-scoped end to end, T11 — Wire Spring Security WebAuthn for passkey registration and passkey sign-in into the one session mechanism · **Wave:** 3 — needs `SignInSessions.resolve` (T4).
- **Lane:** own lane; T11 extends the same `web/security/` package later (it depends on this task).

## Why (user story)

> **As an** Owner
> **I want** to see my active Sign-in Sessions and Passkeys, end any session, and sign out
> **So that** a lost device or a stolen session stops working when I say so
>
> — `spec.md §4, US-46, verbatim` · full text: [spec.md](../spec.md)

This task makes a live Sign-in Session the only way past the front door, and makes every refusal speak the one problem format the SPA routes on.

## Inlined context

> **Authentication:** One Spring Security filter chain in `web`. `SessionCookieSecurityContextRepository` resolves the `telex_session` cookie through `identity`'s `SignInSessions` (ADR-0001). Public routes: sign-in API (request, read grant, redeem by link or code), WebAuthn authentication options and login, `/actuator/health`, static SPA assets and client routes. Everything else needs a live session
> **Session cookie:** `telex_session`: 256-bit random, `HttpOnly`, `SameSite=Lax`, `Path=/`, `Secure` when `TELEX_PUBLIC_URL` is https. Lifetime is enforced server-side: the cookie has a 90-day `Max-Age` as a hint only
> **CSRF:** Spring Security `CookieCsrfTokenRepository` (readable `XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header from the fetch client) on every state-changing request, including the sign-in endpoints and the WebAuthn ceremonies
> **Proxy:** […] The app trusts `X-Forwarded-*` (`server.forward-headers-strategy=framework`). Email links, the cookie `Secure` flag and the WebAuthn RP ID/origins come from `TELEX_PUBLIC_URL`, never from request headers
> **Caching:** API responses carry `Cache-Control: no-store` (Spring Security default), so Cloudflare and the browser never cache Owner data. Hashed static assets can be cached
>
> — `sad.md §8, rows Authentication / Session cookie / CSRF / Proxy / Caching, abridged` · full text: [sad.md](../sad.md)

> The app issues that cookie on every response, the SPA's `index.html` included, so a browser that opens a Sign-in Link first can still post. A missing or wrong token answers `403 forbidden`.
>
> — `contracts/openapi.yaml, info.description, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

> **Required in `implement`:** rename both constants to `validation-failed` / `internal-error`, derive framework codes with `_` → `-`, map Bean Validation field codes (`NotBlank`, `Pattern`…) to kebab labels (`required`, `email-incomplete`, `code-format`), update `ProblemHandlerTest`, and add the rule to `architecture-map.md` §Conventions and `CLAUDE.md` §Errors. […] Security-filter errors (401, 403, WebAuthn failures) don't pass through `ProblemHandler`, so `web/security` needs an entry point, an access-denied handler and WebAuthn failure handlers that render the same problem.
>
> — `contracts/api-sync-report.md §B.2, abridged` · full text: [api-sync-report.md](../contracts/api-sync-report.md)

> `SpaHosting.kt` existing; must not swallow /webauthn/** and /login/webauthn
>
> — `sad.md §5, internal decomposition, verbatim` · full text: [sad.md](../sad.md)

Uses: T4 `SignInSessions.resolve(key, background)`; T2 `PublicUrl.isSecure`. The `X-Telex-Background: 1` header → `background = true` (ADR-0005). WebAuthn endpoint wiring itself is T11; this task only keeps their paths out of `SpaHosting`'s fallback and lists `/webauthn/authenticate/options` + `/login/webauthn` as public.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes (reads/writes `sign_in_session` only through T4's `SignInSessions`).

## API contract

Cross-cutting for every operation in `contracts/openapi.yaml`:

- Security scheme `SessionCookie` (cookie `telex_session`); `security: []` operations are public: `requestSignInEmail`, `previewSignInLink`, `redeemSignInLink`, `redeemSignInCode`, `signOut`, `passkeyAuthenticationOptions`, `signInWithPasskey`; plus `/actuator/health` and SPA assets/routes.
- `NotSignedIn` → `401` `unauthenticated` (no cookie / unknown key) or `session-ended` (ended, idle 30 days, started 90 days ago).
- `Forbidden` → `403` `forbidden` (missing or wrong `X-XSRF-TOKEN`).
- Header `X-Telex-Background: "1"` (optional) — not counted as activity.
- `ErrorCode` = lowercase kebab-case DNS-1123 label (`validation-failed`, `internal-error`, `not-found`, …).

— `contracts/openapi.yaml, components.securitySchemes + responses NotSignedIn/Forbidden + schemas ErrorCode, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-96 — domain invariant

> **Given** a Sign-in Session that has been idle for 30 days (activity means a page the Owner opens or an action they take; background refreshes of an open tab don't count), or that started 90 days ago no matter how active it is
> **When** the Owner next opens teleX in that browser
> **Then** they see the "Session ended" page with a "Sign in again" action
>
> — `spec.md §5, AC-96, verbatim` · full text: [spec.md](../spec.md)

### AC-101 — error

> **Given** a signed-out person who opens a link to a teleX page
> **When** the page needs a Sign-in Session
> **Then** they see the sign-in page, and after signing in they land on the page they originally opened, but only if it is a teleX page; any other destination leads to the Inbox
>
> — `spec.md §5, AC-101, verbatim` · full text: [spec.md](../spec.md)

### AC-95 — happy

> **Given** a signed-in Owner
> **When** they choose "Sign out"
> **Then** they land on the sign-in page, and going back in the browser does not show any of their data
>
> — `spec.md §5, AC-95, verbatim` · full text: [spec.md](../spec.md)

### AC-83 — error

> **Given** the sign-in page
> **When** the person submits something that isn't a valid email address
> **Then** no email is sent, and the field tells them to enter a complete email address, with a name, an @ sign and a domain that contains a dot (for example `me@example.com`; `me@localhost` is refused)
>
> — `spec.md §5, AC-83, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Add `spring-boot-starter-security` (Boot-managed) to `gradle/libs.versions.toml` + `backend/app/build.gradle.kts`.
- [ ] `web/security/SecurityConfiguration` — one `SecurityFilterChain`: public matchers above, everything else `authenticated()`; `CookieCsrfTokenRepository.withHttpOnlyFalse()` + a filter that loads the token on every response; default `Cache-Control: no-store`.
- [ ] `web/security/SessionCookieSecurityContextRepository` → `SignInSessions.resolve`; a request attribute distinguishes `unauthenticated` vs `session-ended` for the entry point.
- [ ] `web/security/SessionCookies` — write/clear `telex_session` with the attributes above (`Secure` from `PublicUrl`), reused by T9 and T11.
- [ ] `AuthenticationEntryPoint` + `AccessDeniedHandler` rendering `problemDetail(...)` JSON.
- [ ] `ProblemHandler`: constants → `validation-failed` / `internal-error`; derived codes `_` → `-`; map `NotBlank`/`NotNull` → `required`, `Pattern` on email → `email-incomplete`, on code → `code-format`; update `ProblemHandlerTest`.
- [ ] `server.forward-headers-strategy: framework` in `application.yaml`.
- [ ] `SpaHosting`: `/webauthn/**` and `/login/webauthn` excluded from the `index.html` fallback (extend `SpaHostingIT`).
- [ ] One line each in `CLAUDE.md` §Errors and `docs/architecture-map.md` §Conventions: codes are kebab-case DNS-1123 labels.
- [ ] `SecurityChainIT` with a throwaway protected test endpoint (or `/api/v1/me` once T10 lands).

## Edge cases

| Case | Behaviour |
|---|---|
| `GET /inbox` (client route) signed out | `index.html` (SPA decides), not 401 |
| Protected `/api/**` with no cookie | `401 unauthenticated` problem JSON |
| Cookie of a session ended elsewhere / idle 30 days | `401 session-ended`; T4 marks it ended |
| Live session + `X-Telex-Background: 1` | handled, `last_activity_at` unchanged |
| POST to a public sign-in endpoint without `X-XSRF-TOKEN` | `403 forbidden` problem JSON |
| First visit to `index.html` | response sets the readable `XSRF-TOKEN` cookie |
| `TELEX_PUBLIC_URL=https://…` behind a proxy | `telex_session` gets `Secure`; `request.isSecure` true via `X-Forwarded-Proto` |

## Definition of Done

- [ ] `SecurityChainIT`, updated `ProblemHandlerTest` and `SpaHostingIT` pass.
- [ ] Every 401/403 body is `application/problem+json` with `type = urn:telex:error:<code>` and a kebab `code`.
- [ ] `Cache-Control: no-store` asserted on an API response (AC-95 server half).
- [ ] every Hard Rule inlined above still holds (session state never in an `HttpSession` except the WebAuthn ceremony, T11).
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green (`web` → `identity` only).
