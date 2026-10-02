---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead", "Security Lead"]
updated_at: "2026-10-02"
feature_size: "M"
ticket: "E01 platform-skeleton"
---

# 0003 — Redeem the Sign-in Link and Sign-in Code as one hashed, single-use grant owned by identity

- **Status:** Accepted
- **Date:** 2026-10-02
- **Deciders:** Anton Husiev, Claude (design Socratic walk)

## Context

One sign-in email carries a Sign-in Link and a 6-digit Sign-in Code. Together they are one permission to sign in:
- it lasts 15 minutes, checked at the confirm or the typed code (AC-35);
- it can be used once (AC-84), and using either half voids the other (AC-82);
- 5 wrong codes void both halves (AC-85);
- a newer email for the same address voids the older one, and a code counts only against the email its page asked for (AC-103);
- the link signs in only after "Continue as <address>" (AC-86).

Spring Security's one-time-token login covers a plain magic link only.

## Decision drivers

- AC-34, AC-35, AC-82, AC-84, AC-85, AC-86, AC-103, AC-104 and spec §6: "15 min, single use", "6 digits, ≤ 5 wrong attempts per email, then void".
- Spec §6.1: link and code never stored in readable form.
- Two concurrent redemptions (double click, link and code at once) must never produce two sessions.

## Considered options

1. **An identity-owned `sign_in_grant` row per sign-in email.** It holds the link-token hash, the code hash, the wrong-attempt count, `expires_at`, `used_at`, `superseded_at` and whether it creates the account. It is redeemed by one atomic conditional `UPDATE`, through our own `/api/sign-in/...` endpoints.
2. **Spring Security `oneTimeTokenLogin()`** with a custom `OneTimeTokenService`.

## Decision outcome

**Chosen:** option 1. Each spec rule is one condition in the redeeming `UPDATE … WHERE used_at IS NULL AND superseded_at IS NULL AND wrong_attempts < 5 AND expires_at > now`. The row returned decides between success and the exact refusal (expired / used / void), which maps to the three refusal pages. A wrong code increments `wrong_attempts` atomically. Issuing a grant first supersedes any live grant for the same canonical address.

Secrets are stored as follows:
- the link token is 256 bits from `SecureRandom`, stored as SHA-256;
- the code is stored as SHA-256 of the grant id plus the code;
- the "Check your email" page holds the grant id, so a code is checked only against its own grant (AC-103).

Success calls `SignInSessions.start(...)` (ADR-0001). It ends any session the browser already carries (AC-104) and, for a new canonical address, creates the Owner (AC-34). Option 2 has no code, no attempt limit and no supersede rule. Its stock JDBC service stores tokens in readable form, so nearly every part would be replaced and only the filter wiring would remain.

## Consequences

**Positive**
- Every refusal rule is explicit, one line each, and testable with a controlled `Clock`.
- Concurrency-safe by construction: the database decides who wins.
- A database dump reveals no usable link. A code hash can be brute-forced offline (10^6 values), but only within the grant's 15 minutes (sad §11 accepted debt).

**Negative**
- Our own endpoints and roughly 250 lines of code instead of a framework feature.
- The confirm page (SCR-08) is our own SPA page, which must not redeem on open (AC-86): opening only reads the grant's state, and only the confirm action redeems.

**Neutral**
- Expired grants stay in the table. No cleanup job in E01.

## Links

- Spec: [[../spec.md]] AC-34, AC-35, AC-82, AC-84, AC-85, AC-86, AC-103, AC-104, §6, §6.1
- SAD: [[../sad.md]] §4
- Related ADR: [[0001-keep-sign-in-sessions-in-an-identity-owned-table-behind-an-opaque-cookie]], [[0004-send-email-through-a-new-mail-integration-module]]
