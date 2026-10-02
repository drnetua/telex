---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead", "Security Lead"]
updated_at: "2026-10-02"
feature_size: "M"
ticket: "E01 platform-skeleton"
---

# 0006 — Derive email links, cookie security and the passkey RP ID from one configured public URL

- **Status:** Accepted
- **Date:** 2026-10-02
- **Deciders:** Anton Husiev, Claude (design Socratic walk — the Operator named the production topology)

## Context

Locally, teleX runs on plain HTTP at `http://localhost:8080` (AC-33). In production it sits behind Cloudflare, which terminates HTTPS and proxies to the app over HTTP. Several things depend on "where teleX lives":
- the Sign-in Link and the sessions link in emails (AC-34, AC-98);
- whether the session cookie is `Secure`;
- the WebAuthn relying-party ID (RP ID) and allowed origins (AC-89).

Passkeys are bound to the RP ID: if the domain changes, every existing Passkey stops working. Building links from the request's `Host` header would let a forged header put an attacker's domain into a sign-in email.

## Decision drivers

- AC-33 (works on localhost with zero configuration), AC-34 / AC-98 (links in emails), AC-89 and spec §6 passkey browser coverage.
- Production behind a TLS-terminating proxy (Cloudflare), stated by the Operator during design.
- Spec §6.1: a teleX session is the key to the Owner's Telegram from E02 on.

## Considered options

1. **One setting, `TELEX_PUBLIC_URL`** (default `http://localhost:8080`). Email links are built from it; the cookie is `Secure` when its scheme is `https`; the WebAuthn RP ID is its host and the allowed origin is its origin. The app also trusts `X-Forwarded-*` from the proxy (`server.forward-headers-strategy=framework`) so redirects and `request.isSecure` agree.
2. **Derive everything per request** from `Host` and `X-Forwarded-Proto` / `X-Forwarded-Host`.

## Decision outcome

**Chosen:** option 1. It gives one explicit source of truth, a zero-config default for the one-command install, and immunity to `Host`-header injection in emails. The RP ID can only change when the Operator deliberately changes this setting. Option 2 needs no setting, but it puts whatever host a request claims into emails and passkey registrations, and a mis-set proxy would silently register passkeys under the wrong RP ID.

## Consequences

**Positive**
- Emails always point at the real installation, whatever the request says.
- Moving from localhost to Cloudflare is one environment variable, with no code paths that differ by environment.

**Negative**
- Changing the production domain later makes every registered Passkey unusable (they are bound to the old RP ID). Owners fall back to email sign-in and must add new Passkeys (sad §11).
- Developing through the Vite dev server (`pnpm dev`, port 5173) needs that origin in the allowed WebAuthn origins and Vite proxies for `/webauthn/**` and `/login/webauthn`. This is set in the `local` profile only.

**Neutral**
- Passkeys need a secure context: `localhost` qualifies, and so does HTTPS via Cloudflare. Opening teleX on a phone through the laptop's LAN IP over HTTP can't use Passkeys, but email sign-in and the Sign-in Code still work (AC-82).

## Links

- Spec: [[../spec.md]] AC-33, AC-34, AC-82, AC-89, AC-98, §6, §6.1
- SAD: [[../sad.md]] §7
- Related ADR: [[0001-keep-sign-in-sessions-in-an-identity-owned-table-behind-an-opaque-cookie]], [[0002-use-spring-security-webauthn-for-passkeys]]
