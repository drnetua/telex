---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead", "Security Lead"]
updated_at: "2026-10-02"
feature_size: "M"
ticket: "E01 platform-skeleton"
---

# 0001 — Keep Sign-in Sessions in an identity-owned table behind an opaque, hashed cookie

- **Status:** Accepted
- **Date:** 2026-10-02
- **Deciders:** Anton Husiev, Claude (design Socratic walk)

## Context

A Sign-in Session is the signed-in state of one Owner in one browser. The spec requires more than a plain session cookie:
- an Owner can list their sessions, with browser, device type and last activity (AC-93);
- an Owner can end any one session (AC-93) or all but the current one (AC-94);
- a session ends after 30 days idle, where background refreshes don't count, or 90 days after it started (AC-96);
- a new session in a browser replaces the one already there (AC-104).

The session key must never be stored in readable form (spec §6.1). The scaffold has no session store and no Spring Security yet (sad §3 brownfield note).

## Decision drivers

- AC-93, AC-94, AC-96, AC-97, AC-104 and spec §6 "Sign-in Session lifetime: ends after 30 days idle or 90 days from start".
- Spec §6.1: a session key is never stored, shown back or logged in readable form.
- Quality goal 1 (sad §1): no silent takeover; revocation takes effect on the next request.
- Repo convention: Owner-owned rows carry `owner_id`, every query filters on it; UUIDv7 ids.

## Considered options

1. **Identity-owned `sign_in_session` table + opaque random cookie** — 256-bit random key in an `HttpOnly` cookie, only its SHA-256 hash stored; a custom Spring Security `SecurityContextRepository` resolves it on each request.
2. **Spring Session JDBC** — the framework's `SPRING_SESSION` tables with per-principal lookup and deletion.
3. **Signed JWT in a cookie + a revocation table** — stateless tokens, with a deny-list for ended sessions.

## Decision outcome

**Chosen:** option 1. Each spec rule maps to one column or one condition:
- `started_at` gives the 90-day cap;
- `last_activity_at`, updated only by user-initiated requests (ADR-0005), gives the 30-day idle rule;
- `ended_at` covers revocation and sign-out;
- `user_agent_label`, `device_type` and `time_zone` feed the list (AC-93) and the new-sign-in email (AC-98).

The key is stored only as a hash. Option 2 bumps the last-access time on every request, including background refreshes, which breaks AC-96 without patching the framework. It has no absolute lifetime, and it stores the session id in readable form, which breaks §6.1. Option 3 still needs a table and a database lookup on every request to honour revocation, so it keeps the complexity of tokens (expiry, rotation) without the benefit of being stateless.

## Consequences

**Positive**
- The 30/90-day rules, revocation, the per-device list and AC-104 replacement are plain SQL conditions, tested with a controlled `Clock`.
- A stolen database dump contains no usable session keys.
- Every sign-in method (link, code, Passkey via ADR-0002) ends in the same `SignInSessions.start(...)` call.

**Negative**
- About 300 lines of our own code (repository, `SecurityContextRepository` bridge in `web`, cookie handling) instead of a library.
- With a cookie session, CSRF protection is ours to configure. We use Spring Security's cookie-to-header CSRF token (sad §8).
- One indexed lookup by `key_hash` per authenticated request.

**Neutral**
- Expired and ended rows stay in the table. No cleanup job in E01 (sad §11 accepted debt).
- Switching to Spring Session later would invalidate every live session (everyone signs in again), but no Owner data is lost.

## Links

- Spec: [[../spec.md]] AC-93, AC-94, AC-96, AC-97, AC-104, §6, §6.1
- SAD: [[../sad.md]] §4
- Related ADR: [[0003-redeem-sign-in-link-and-code-as-one-hashed-single-use-grant]], [[0005-count-session-activity-only-from-requests-the-spa-does-not-mark-as-background]], [[0006-derive-links-cookie-security-and-passkey-rp-id-from-one-public-url]]
