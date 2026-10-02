---
id: T2
title: "Add identity domain primitives: typed ids, Clock, email canonicalisation, secrets, device naming and the public URL"
layer: "domain"
deps: []
blocks: ["T4", "T5"]
acs: ["AC-34", "AC-83"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/Ids.kt", "backend/app/src/main/kotlin/telex/identity/PublicUrl.kt", "backend/app/src/main/kotlin/telex/identity/internal/owner/EmailAddress.kt", "backend/app/src/main/kotlin/telex/identity/internal/secret/", "backend/app/src/main/kotlin/telex/identity/internal/device/", "backend/app/src/main/kotlin/telex/ClockConfiguration.kt", "backend/app/src/main/resources/application.yaml", "backend/app/src/test/kotlin/telex/identity/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T2 — Add identity domain primitives: typed ids, Clock, email canonicalisation, secrets, device naming and the public URL

## Place in the sequence

- **Blocked by:** — · **Blocks:** T4 — Build the Owner and Sign-in Session core: start, resolve with the 30/90-day rules, end by key, and the SignInSessionStarted event, T5 — Issue a Sign-in Grant with its email and read a Sign-in Link without redeeming it · **Wave:** 1 — pure Kotlin with no DB; starts in parallel with T1, T3 and T13.
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to sign up and sign in with just my email address, using the link or the code from the email
> **So that** I never create or remember a password, on whichever device I read my email
>
> — `spec.md §4, US-01, verbatim` · full text: [spec.md](../spec.md)

This task supplies the pure rules every later identity task reuses: what counts as a complete address, when two addresses are one account, how secrets are made and hashed, and how a browser is named.

## Inlined context

> **Email identity:** `email_canonical` = lowercase, with the `+tag` removed from the local part, and unique across the installation, so one canonical address is one Owner (AC-34). `email_as_created` is kept for the new-sign-in email. A sign-in email goes to the address as typed that time. Validation: a name, an `@`, and a domain containing a dot (AC-83)
>
> **Secrets:** `SecureRandom` everywhere. Link token and session key: 256 bits, stored as SHA-256. Code: 6 digits, stored as SHA-256(grant id + code). Never returned by any API after issue
>
> **Time:** One injectable `java.time.Clock` bean (fixed in tests). `timestamptz` in UTC. […]
>
> **Device naming:** In-house User-Agent mapper → "<Browser> on <Device>" and a device type (phone / tablet / computer), with a neutral fallback for unknown agents. Used for the session list, the passkey label and the email
>
> **ID strategy:** UUIDv7 typed ids: `OwnerId`, `SignInGrantId`, `SignInSessionId`. Exception: a Passkey is keyed by its WebAuthn credential id (ADR-0002)
>
> — `sad.md §8, rows Email identity / Secrets / Time / Device naming / ID strategy, abridged` · full text: [sad.md](../sad.md)

> **IDs:** app-generated UUIDv7 via `telex.shared.Uuid7.next()`, typed per aggregate as `@JvmInline value class XId(override val value: UUID) : TypedId` (ADR-0003).
>
> — `CLAUDE.md §Layout and code conventions, IDs, verbatim` · full text: [CLAUDE.md](../../../../CLAUDE.md)

> **One setting, `TELEX_PUBLIC_URL`** (default `http://localhost:8080`). Email links are built from it; the cookie is `Secure` when its scheme is `https`; the WebAuthn RP ID is its host and the allowed origin is its origin.
>
> — `adr/0006 §Considered options, option 1 (chosen), abridged` · full text: [adr/0006](../adr/0006-derive-links-cookie-security-and-passkey-rp-id-from-one-public-url.md)

> `EmailAddress`: `maxLength: 254`, `pattern: "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"` — a non-empty name, one `@`, and a domain containing a dot (`me@localhost` is refused, AC-83). Validated by the service with the same rule as the SPA.
>
> — `contracts/openapi.yaml, schema EmailAddress, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

**Recorded deviation (breakdown decision):** sad §5 lists `PublicUrl` under `web/security`, but `identity`'s email templates (T5, T7) need the same URL and `identity` must not depend on `web`. `PublicUrl` therefore lives at the `identity` root as public API (bound from `telex.public-url`, env `TELEX_PUBLIC_URL`), and `web` reads it from there. Still one setting — ADR-0006 holds. Note it in the PR.

**Device types:** `phone`, `tablet`, `computer`, `unknown` (data-model CHECK; the 4-value list wins over sad §8's three + fallback — `api-sync-report.md §B.3`).

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — no API surface. Kotlin surface this task adds:

- `telex.identity`: `OwnerId`, `SignInGrantId`, `SignInSessionId` (`TypedId`, `Uuid7.next()`); `PublicUrl` (`origin`, `host` = RP ID, `isSecure`, `link(path, fragment?)`).
- `telex.identity.internal.owner.EmailAddress` — `parse(raw): EmailAddress?` (null = incomplete), `.asTyped`, `.canonical`.
- `telex.identity.internal.secret` — 256-bit Base64URL token, 6-digit code, `sha256(token)`, `codeHash(grantId, code)`.
- `telex.identity.internal.device` — `DeviceLabel(label, deviceType)` from a `User-Agent`, label ≤ 100 chars.
- `telex.ClockConfiguration` — a `Clock` bean (`Clock.systemUTC()`), overridable in tests.

## Acceptance criteria

### AC-34 — happy

> **Given** a person whose email address has no teleX account
> **When** they enter the address on the sign-in page, open the Sign-in Link from the email and confirm "Continue as <address>"
> **Then** teleX creates their Owner account, signs them in in that browser, offers to create a Passkey, and afterwards shows the empty Inbox with the single step "Connect Telegram"; signing in again with the same address opens the same account, never a second one. Two addresses are the same when they match after ignoring letter case and any "+tag" before the @ (so `Anton+work@Mail.com` and `anton@mail.com` are one account). A sign-in email goes to the address exactly as typed that time; the "New sign-in to teleX" email goes to the address the account was created with
>
> — `spec.md §5, AC-34, verbatim` · full text: [spec.md](../spec.md)

### AC-83 — error

> **Given** the sign-in page
> **When** the person submits something that isn't a valid email address
> **Then** no email is sent, and the field tells them to enter a complete email address, with a name, an @ sign and a domain that contains a dot (for example `me@example.com`; `me@localhost` is refused)
>
> — `spec.md §5, AC-83, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Typed ids in `backend/app/src/main/kotlin/telex/identity/Ids.kt`, following `telex.shared.Ids` (`IdsTest` shows the pattern).
- [ ] `EmailAddress` with the openapi pattern + 254 limit; canonical = lowercase whole address, drop `+…` from the local part.
- [ ] Secret generator + hashing in `identity/internal/secret/` (`SecureRandom`, `MessageDigest("SHA-256")`), returning 32-byte digests.
- [ ] User-Agent mapper in `identity/internal/device/` covering Chrome, Safari, Firefox, Edge on Mac, Windows, Linux, iPhone, iPad, Android; neutral fallback label + `unknown`.
- [ ] `PublicUrl` `@ConfigurationProperties` at the identity root; `telex.public-url: ${TELEX_PUBLIC_URL:http://localhost:8080}` in `application.yaml`.
- [ ] `Clock` bean in `backend/app/src/main/kotlin/telex/ClockConfiguration.kt`.
- [ ] Unit tests under `backend/app/src/test/kotlin/telex/identity/`.

## Edge cases

| Case | Behaviour |
|---|---|
| `Anton+work@Mail.com` | canonical `anton@mail.com`; `asTyped` unchanged (AC-34) |
| `anton+@mail.com` | canonical `anton@mail.com` |
| `me@localhost`, `@example.com`, `me@`, `a@b@c.com`, `me @x.com` | incomplete → `null` (AC-83) |
| Address longer than 254 chars | incomplete → `null` |
| Unknown or missing User-Agent | neutral label (pick e.g. "Unknown browser"; upstream gives no copy — note it in the PR) and `unknown` |
| `TELEX_PUBLIC_URL=https://telex.example.test` | `isSecure = true`, `host = telex.example.test`, `origin = https://telex.example.test` |
| `TELEX_PUBLIC_URL` with a trailing slash or a path | normalised to scheme + host (+ port); links never double the slash |

## Definition of Done

- [ ] Unit tests cover every row above and pass under `./gradlew :backend:app:test`.
- [ ] Hashes are 32 bytes (matches the T1 CHECK constraints); no secret is ever `toString`-ed or logged.
- [ ] `ModularityTest` green (everything new is inside `identity` or the root package).
- [ ] every Hard Rule inlined above still holds.
- [ ] `./gradlew spotlessCheck detekt` clean.
