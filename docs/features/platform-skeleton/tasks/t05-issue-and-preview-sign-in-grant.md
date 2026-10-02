---
id: T5
title: "Issue a Sign-in Grant with its email and read a Sign-in Link without redeeming it"
layer: "app"
deps: ["T1", "T2", "T3"]
blocks: ["T6", "T9"]
acs: ["AC-83", "AC-86", "AC-103", "AC-35"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/SignIn.kt", "backend/app/src/main/kotlin/telex/identity/internal/grant/", "backend/app/src/main/kotlin/telex/identity/internal/email/", "backend/app/src/integrationTest/kotlin/telex/identity/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T5 — Issue a Sign-in Grant with its email and read a Sign-in Link without redeeming it

## Place in the sequence

- **Blocked by:** T1 — Promote the four staged identity migrations into the live Flyway tree, T2 — Add identity domain primitives: typed ids, Clock, email canonicalisation, secrets, device naming and the public URL, T3 — Create the mail integration module with the Mailer port and the SMTP adapter · **Blocks:** T6 — Redeem a Sign-in Link or Sign-in Code atomically and start the Sign-in Session, T9 — Expose the sign-in REST endpoints: request email, preview and redeem link, redeem code, sign out · **Wave:** 2 — needs the grant table (T1), the primitives (T2) and the Mailer port (T3).
- **Lane:** shares `identity/SignIn.kt` and `identity/internal/grant/` with T6 (T6 depends on this task, so they serialize).

## Why (user story)

> **As an** Owner
> **I want** to sign up and sign in with just my email address, using the link or the code from the email
> **So that** I never create or remember a password, on whichever device I read my email
>
> — `spec.md §4, US-01, verbatim` · full text: [spec.md](../spec.md)

This task sends the sign-in email and lets the confirm page show "Continue as <address>" without ever signing anyone in by just opening the link.

## Inlined context

> **Email sign-in is an identity-owned grant, redeemed atomically.** One `sign_in_grant` row per sign-in email holds hashes of the link token and the code plus the counters that enforce 15 minutes, single use, 5 wrong codes and "newest email wins". […] Opening the link only reads the grant, and only the explicit confirm redeems it, which keeps mail scanners harmless (AC-86).
>
> — `sad.md §4, strategic choice 2, abridged` · full text: [sad.md](../sad.md) · decision: [adr/0003](../adr/0003-redeem-sign-in-link-and-code-as-one-hashed-single-use-grant.md)

> Issuing a grant first supersedes any live grant for the same canonical address. […] the "Check your email" page holds the grant id, so a code is checked only against its own grant (AC-103).
>
> — `adr/0003 §Decision outcome, abridged` · full text: [adr/0003](../adr/0003-redeem-sign-in-link-and-code-as-one-hashed-single-use-grant.md)

> Flow US-01 request a sign-in email: *not a complete address* → field error, the service applies the same rule, no email is sent (AC-83). *Complete* → canonicalise → mark every live grant for the canonical address superseded → insert a new grant with hashes of the link token and the code, expiring in 15 minutes → send the sign-in email with link and code to the address as typed, inside the request. *Mail server unavailable or too slow* → roll back, no grant is kept and no earlier grant is superseded → server failure. *Accepted* → grant id for the code page, never the link or the code.
>
> — `sad.md §6, Flow US-01 request a sign-in email, abridged` · full text: [sad.md](../sad.md)

> Secrets — the link token, the code, the session key — are never returned after issue and never appear in a URL the server sees: the Sign-in Link points to an SPA route with the token in the URL fragment, and the SPA posts it in a body.
>
> — `contracts/openapi.yaml, info.description, verbatim` · full text: [openapi.yaml](../contracts/openapi.yaml)

**SPA route for the link (set by this breakdown, shared with T13/T15):** `PublicUrl.link("/sign-in/link", fragment = token)` → `http://localhost:8080/sign-in/link#<token>`. Email copy (sentence case, no emoji): subject "Sign in to teleX"; body names the link, the 6-digit code and "The link and the code work once and expire in 15 minutes."

> **Hard rule (spec §6.1):** a Sign-in Link, a Sign-in Code or a session key is never stored, shown back or logged in readable form.
>
> — `spec.md §6.1, Secrets, abridged` · full text: [spec.md](../spec.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Column | Constraints | Change |
|---|---|---|
| `sign_in_grant.id` | PK UUIDv7 (`SignInGrantId`) | write |
| `email` / `canonical_email` | VARCHAR(254); canonical lowercase | write |
| `link_token_hash` | UNIQUE, 32 bytes — preview lookup | write/read |
| `code_hash` | 32 bytes = SHA-256(grant id + code) | write |
| `issued_at` / `expires_at` | `expires_at = issued_at + 15 min` | write/read |
| `used_at` / `superseded_at` / `wrong_attempts` | read for preview refusal; `superseded_at` set on live grants of the same canonical address (`sign_in_grant_live_by_canonical_email_idx`) | read/write |

— `data-model.md §Aggregate: Sign-in Grant, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — HTTP mapping is T9. Kotlin API at the `identity` root (`SignIn`):

- `request(rawEmail: String): GrantIssued(grantId, emailAsTyped)` · throws a `DomainProblem` `400 validation-failed` with field error `email` / `email-incomplete` · `503 mail-unavailable` when `Mailer` throws.
- `preview(linkToken: String): LinkPreview(email)` · throws `410` `sign-in-link-expired` (expired, superseded, or unknown token — unknown has **no** `email`), `sign-in-link-used`, `sign-in-grant-void`; known grants carry the `email` problem extension.

— `contracts/openapi.yaml, operationIds requestSignInEmail + previewSignInLink, responses SignInGrantRefused, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-83 — error

> **Given** the sign-in page
> **When** the person submits something that isn't a valid email address
> **Then** no email is sent, and the field tells them to enter a complete email address, with a name, an @ sign and a domain that contains a dot (for example `me@example.com`; `me@localhost` is refused)
>
> — `spec.md §5, AC-83, verbatim` · full text: [spec.md](../spec.md)

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

### AC-35 — error

> **Given** a sign-in email sent more than 15 minutes ago
> **When** the person opens its Sign-in Link, confirms "Continue as <address>" on a link page opened earlier, or types its Sign-in Code
> **Then** sign-in is refused, because the 15 minutes are checked at the confirm or the typed code, not only when the link is opened; they see that the link has expired and a "Send a new link" action that emails a fresh link to the same address
>
> — `spec.md §5, AC-35, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] SignInGrant aggregate + repository in `identity/internal/grant/` (insert, supersede-live-by-canonical, find-by-link-hash).
- [ ] Sign-in email template in `identity/internal/email/` (plain text; link from `PublicUrl`; code).
- [ ] `SignIn.request` in one `@Transactional`: validate → supersede → insert → `Mailer.send` (throw → rollback → `mail-unavailable`).
- [ ] `SignIn.preview` read-only (`@Transactional(readOnly = true)`), classify refusals; extract the classifier so T6 reuses it.
- [ ] Refusal problems as `DomainProblem` subclasses with the `email` extension.
- [ ] Counter `telex.signin.grants.issued` (sad §7).
- [ ] `SignInIssueIT` using `RecordingMailer` (T3) and a fixed `Clock`; fixture `aGrant(...)`.

## Edge cases

| Case | Behaviour |
|---|---|
| `me@localhost` | `400` field error `email-incomplete`, no grant row, no email (AC-83) |
| Second request for `Anton+work@Mail.com` after `anton@mail.com` | the first grant gets `superseded_at`; only the newest works (AC-103) |
| Mailer throws | no new grant, earlier grant **not** superseded, `503 mail-unavailable` |
| Preview at 14:05 of a 14:00 grant | `200` with `email`; nothing changes in the row (AC-86) |
| Preview at 14:16 | `410 sign-in-link-expired` + `email` (AC-35) |
| Preview of a used / void (5 wrong) grant | `410 sign-in-link-used` / `sign-in-grant-void` + `email` |
| Preview of a token that matches nothing | `410 sign-in-link-expired` without `email` (api-sync-report §B.4) |

## Definition of Done

- [ ] `SignInIssueIT` covers every edge case and passes.
- [ ] The captured email contains the link and the code; `sign_in_grant` contains neither in readable form (QG-1d, grant part).
- [ ] No address, token or code in the log output of the test run.
- [ ] every Hard Rule inlined above still holds.
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green.
