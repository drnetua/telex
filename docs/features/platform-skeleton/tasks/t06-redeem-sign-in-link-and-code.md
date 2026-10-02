---
id: T6
title: "Redeem a Sign-in Link or Sign-in Code atomically and start the Sign-in Session"
layer: "app"
deps: ["T4", "T5"]
blocks: ["T9"]
acs: ["AC-34", "AC-82", "AC-35", "AC-84", "AC-85", "AC-103", "AC-104"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/SignIn.kt", "backend/app/src/main/kotlin/telex/identity/internal/grant/", "backend/app/src/integrationTest/kotlin/telex/identity/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T6 — Redeem a Sign-in Link or Sign-in Code atomically and start the Sign-in Session

## Place in the sequence

- **Blocked by:** T4 — Build the Owner and Sign-in Session core: start, resolve with the 30/90-day rules, end by key, and the SignInSessionStarted event, T5 — Issue a Sign-in Grant with its email and read a Sign-in Link without redeeming it · **Blocks:** T9 — Expose the sign-in REST endpoints: request email, preview and redeem link, redeem code, sign out · **Wave:** 3 — needs session start (T4) and the grant aggregate (T5).
- **Lane:** shares `identity/SignIn.kt` and `identity/internal/grant/` with T5 — serialized after it.

## Why (user story)

> **As an** Owner
> **I want** to sign up and sign in with just my email address, using the link or the code from the email
> **So that** I never create or remember a password, on whichever device I read my email
>
> — `spec.md §4, US-01, verbatim` · full text: [spec.md](../spec.md)

This task turns a confirmed link or a typed code into a signed-in browser, exactly once, and refuses every stale, reused or guessed attempt.

## Inlined context

> **Chosen:** option 1. Each spec rule is one condition in the redeeming `UPDATE … WHERE used_at IS NULL AND superseded_at IS NULL AND wrong_attempts < 5 AND expires_at > now`. The row returned decides between success and the exact refusal (expired / used / void), which maps to the three refusal pages. A wrong code increments `wrong_attempts` atomically. […]
> Success calls `SignInSessions.start(...)` (ADR-0001). It ends any session the browser already carries (AC-104) and, for a new canonical address, creates the Owner (AC-34).
>
> — `adr/0003 §Decision outcome, abridged` · full text: [adr/0003](../adr/0003-redeem-sign-in-link-and-code-as-one-hashed-single-use-grant.md)

> **Redeem (ADR-0003):** `UPDATE sign_in_grant SET used_at = :now WHERE <key> AND used_at IS NULL AND superseded_at IS NULL AND wrong_attempts < 5 AND expires_at > :now RETURNING …`. No row → re-read by key to classify the refusal (expired / used / void / superseded = expired).
> A concurrent first sign-in by two browsers resolves through `INSERT … ON CONFLICT (canonical_email) DO NOTHING` + re-read.
>
> — `data-model.md §Aggregate: Sign-in Grant + §Aggregate: Owner, verbatim` · full text: [data-model.md](../data-model.md)

> Flow US-01 sign in by code: find the grant by id; *expired or superseded* → link expired (AC-35, AC-103); *already used by its link or its code* → already used (AC-84); *voided by 5 wrong codes* → grant void (AC-85); *wrong code* → conditional update adds one wrong attempt and voids the grant at the 5th → if 5th: grant void, else wrong code with the attempts left; *correct and usable* → conditional update marks the grant used, which also kills its link (AC-82) → create the Owner if the canonical address is new → end the session this browser held → insert the new session with its key hash, browser, device type and time zone → record the session-started event → set the session cookie, with the created-account flag.
>
> — `sad.md §6, Flow US-01 sign in by Sign-in Code, abridged` · full text: [sad.md](../sad.md) · link variant: Critical flow 1 (same branches, checked again at confirm)

> **Owner email:** the address exactly as typed when the account was created; the "New sign-in to teleX" email goes here (AC-34, AC-98). The grant's `email` becomes `owner.email` if this redeem creates the account.
>
> — `data-model.md §owner.email + §sign_in_grant.email, abridged` · full text: [data-model.md](../data-model.md)

Uses: T4 `SignInSessions.start(...)` + `Owners.findOrCreate(...)`; T5's grant repository and refusal classifier; T2 `codeHash(grantId, code)`. Counters `telex.signin.redeemed{method=link|code}`, `telex.signin.refused{reason=expired|used|void|wrong_code}` (sad §7).

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Column | Use | Change |
|---|---|---|
| `sign_in_grant.used_at` | set by the redeeming conditional `UPDATE` (by `link_token_hash` or by `id` + `code_hash`) | write |
| `sign_in_grant.wrong_attempts` | `UPDATE … SET wrong_attempts = wrong_attempts + 1 WHERE id = :id AND wrong_attempts < 5 AND used_at IS NULL AND superseded_at IS NULL AND expires_at > :now RETURNING wrong_attempts` | write |
| `owner` | find-or-create by `canonical_email` (via T4) | write |
| `sign_in_session` | end held + insert new (via T4) | write |

— `data-model.md §Aggregate: Sign-in Grant, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — HTTP mapping is T9. Kotlin API on `SignIn`:

- `redeemByLink(linkToken, heldKey?, userAgent?, timeZone?): SignedIn(key, createdAccount)`
- `redeemByCode(grantId, code, heldKey?, userAgent?, timeZone?): SignedIn(key, createdAccount)`
- Refusals (problems): `410 sign-in-link-expired` · `410 sign-in-link-used` · `410 sign-in-grant-void` (each with `email` when the grant exists) · `422 sign-in-code-wrong` with `attemptsLeft` = 5 − `wrong_attempts` (1–4). An unknown grant id → `410 sign-in-link-expired` without `email`.

— `contracts/openapi.yaml, operationIds redeemSignInLink + redeemSignInCode, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

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

### AC-35 — error

> **Given** a sign-in email sent more than 15 minutes ago
> **When** the person opens its Sign-in Link, confirms "Continue as <address>" on a link page opened earlier, or types its Sign-in Code
> **Then** sign-in is refused, because the 15 minutes are checked at the confirm or the typed code, not only when the link is opened; they see that the link has expired and a "Send a new link" action that emails a fresh link to the same address
>
> — `spec.md §5, AC-35, verbatim` · full text: [spec.md](../spec.md)

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

### AC-103 — domain invariant

> **Given** a person who asked for a sign-in email and then asked for another one for the same address
> **When** they open the link or type the code from the earlier email
> **Then** sign-in is refused as expired, with a "Send a new link" action; only the newest email for an address works, and a code typed on a "Check your email" page counts only against the email that this page asked for
>
> — `spec.md §5, AC-103, verbatim` · full text: [spec.md](../spec.md)

### AC-104 — domain invariant

> **Given** a browser that is already signed in, as the same Owner or as another one
> **When** a Sign-in Link is confirmed or a Sign-in Code is typed in that browser
> **Then** the existing Sign-in Session in that browser ends and a new one starts for the Owner the email was sent to, with the "New sign-in to teleX" email per AC-98
>
> — `spec.md §5, AC-104, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Conditional redeem `UPDATE … RETURNING` and wrong-attempt `UPDATE` in the grant repository (`identity/internal/grant/`).
- [ ] `SignIn.redeemByLink` / `redeemByCode` in one transaction each: redeem → find-or-create Owner (grant `email` as typed) → `SignInSessions.start(createdAccount = created)`.
- [ ] Code comparison against `code_hash` only for the grant id the page holds (AC-103); constant-time compare.
- [ ] Counters per sad §7.
- [ ] `SignInRedeemIT` with a fixed `Clock` and the `aGrant`/`aSession` fixtures.

## Edge cases

| Case | Behaviour |
|---|---|
| Link opened 14:05, confirmed 15:01 (grant issued 14:00) | `410 sign-in-link-expired` — checked at confirm (QG-1a) |
| Redeem at 14:59 | signed in |
| Two concurrent redeems of one grant | exactly one `SignedIn`; the other `410 sign-in-link-used` |
| Code redeemed, then the link confirmed | link → `410 sign-in-link-used` (AC-82) |
| 5 wrong codes, then the right code, then the link | 5th → `410 sign-in-grant-void`; right code and link → `sign-in-grant-void` (QG-1b) |
| 3 wrong codes | `422`, `attemptsLeft` 4, 3, 2 |
| Code typed against an earlier grant id after a newer email | `410 sign-in-link-expired` (AC-103) |
| Code not 6 digits | not counted — handled as `400 code-format` by T9 before calling `SignIn` |
| `Anton+work@Mail.com` signs in after `anton@mail.com` owns the account | same `OwnerId`, `createdAccount = false` (AC-34) |
| Browser already signed in as another Owner | old session ended, new one for the grant's Owner (AC-104) |

## Definition of Done

- [ ] `SignInRedeemIT` covers every edge case and passes.
- [ ] Exactly one `owner` row per canonical address after concurrent first redeems.
- [ ] `createdAccount` is true only for the redeem that created the Owner.
- [ ] every Hard Rule inlined above still holds.
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green.
