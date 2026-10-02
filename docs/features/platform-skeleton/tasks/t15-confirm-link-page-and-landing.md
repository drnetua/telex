---
id: T15
title: "Build Confirm sign-in link (SCR-08) and the landing-after-sign-in rule"
layer: "ui"
deps: ["T14"]
blocks: ["T16", "T20"]
acs: ["AC-86", "AC-35", "AC-34", "AC-101"]
files_hint: ["frontend/src/pages/confirm-link/", "frontend/src/app/landing.ts", "frontend/src/api/signIn.ts", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T15 — Build Confirm sign-in link (SCR-08) and the landing-after-sign-in rule

## Place in the sequence

- **Blocked by:** T14 — Build Sign in (SCR-01, email part) and Check your email (SCR-07) with the Sign-in Code · **Blocks:** T16 — Build Create a passkey (SCR-09) and "Sign in with a passkey" on SCR-01, T20 — Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator · **Wave:** 3 — reuses the auth layout and refusal card from T14.
- **Lane:** shares `frontend/src/api/signIn.ts` with T14 (serialized after it).

## Why (user story)

> **As an** Owner
> **I want** to sign up and sign in with just my email address, using the link or the code from the email
> **So that** I never create or remember a password, on whichever device I read my email
>
> — `spec.md §4, US-01, verbatim` · full text: [spec.md](../spec.md)

This task builds the page a Sign-in Link opens, which signs in only on an explicit confirm, and the rule for where every sign-in lands.

## Inlined context

**SCR-08 states:**
- `loading` — link opened; `previewSignInLink` reads the grant without redeeming it (AC-86): auth layout + `LoadState state="loading"` (`rows=2`).
- `default` — `200`: h1 "Sign in to teleX", "Confirm to sign in on this device.", `Button` primary block "Continue as `<address>`", `small` "Nothing happens until you continue."
- `submitting` — `redeemSignInLink` with the browser time zone and the held session: busy "Signing in".
- `refused-expired` / `refused-used` / `refused-void` — `410` **on open or on confirm**: the SCR-07 refusal card, using `email` from the problem.
- `link-unusable` — `410` without `email` (unknown token) or `400` (no token in the fragment): h1 "This link can't be used", `EmptyState kind="blocked"` (icon `ban`) "Open the newest email from teleX, or sign in again.", action "Back to sign in" → SCR-01.
- `resending` — busy; `201` → SCR-07 for the same address in this browser. `error` → shared routing. `success` → `createdAccount` ? SCR-09 : remembered page or SCR-10; a link confirmed in a different browser finds nothing remembered → SCR-10.

— `screens.md §SCR-08, abridged` · full text: [screens.md](../screens.md) · wireframes W-08a/b

> Flow US-01 landing after sign-in: *this sign-in created the account* → SCR-09 Create a passkey, then SCR-10, never a remembered page (AC-34). *Existing account* → read and clear the remembered destination of this browser → *a relative teleX path starting with a single slash* → the page they originally opened (AC-101) → *nothing remembered, or any other destination* → SCR-10 Inbox (AC-101).
>
> — `sad.md §6, Flow US-01 landing after sign-in, abridged` · full text: [sad.md](../sad.md)

**Token source:** route `/sign-in/link#<token>` (T13 route table) — read `location.hash`, post it in the body, never put it in a query string. **Reuse:** T14 `AuthLayout`, `RefusalCard`; T13 `takeRememberedDestination()`; port `LoadState` from `docs/docs/design-system/components/LoadState/`. After this lands, switch SCR-07's success navigation (T14) to the shared `landing` helper.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

- `previewSignInLink` `POST /api/v1/sign-in/link/preview` `{linkToken}` → `200 {email}` · `400` · `410` SignInGrantRefused (+`email` when the grant exists).
- `redeemSignInLink` `POST /api/v1/sign-in/link/redeem` `{linkToken}` + `X-Telex-Time-Zone` → `200 {createdAccount}` + `telex_session` cookie · `400` · `410`.
- `requestSignInEmail` (resend) as in T14.

— `contracts/openapi.yaml, operationIds previewSignInLink + redeemSignInLink, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-86 — domain invariant

> **Given** a Sign-in Link opened by a mail scanner or a link preview that never confirms
> **When** the person later opens the same link and confirms "Continue as <address>" within 15 minutes
> **Then** they are signed in normally, because a link signs in only after the confirm step
>
> — `spec.md §5, AC-86, verbatim` · full text: [spec.md](../spec.md)

### AC-35 — error

> **Given** a sign-in email sent more than 15 minutes ago
> **When** the person opens its Sign-in Link, confirms "Continue as <address>" on a link page opened earlier, or types its Sign-in Code
> **Then** sign-in is refused, because the 15 minutes are checked at the confirm or the typed code, not only when the link is opened; they see that the link has expired and a "Send a new link" action that emails a fresh link to the same address
>
> — `spec.md §5, AC-35, verbatim` · full text: [spec.md](../spec.md)

### AC-34 — happy

> **Given** a person whose email address has no teleX account
> **When** they enter the address on the sign-in page, open the Sign-in Link from the email and confirm "Continue as <address>"
> **Then** teleX creates their Owner account, signs them in in that browser, offers to create a Passkey, and afterwards shows the empty Inbox with the single step "Connect Telegram"; signing in again with the same address opens the same account, never a second one. Two addresses are the same when they match after ignoring letter case and any "+tag" before the @ (so `Anton+work@Mail.com` and `anton@mail.com` are one account). A sign-in email goes to the address exactly as typed that time; the "New sign-in to teleX" email goes to the address the account was created with
>
> — `spec.md §5, AC-34, verbatim` · full text: [spec.md](../spec.md)

### AC-101 — error

> **Given** a signed-out person who opens a link to a teleX page
> **When** the page needs a Sign-in Session
> **Then** they see the sign-in page, and after signing in they land on the page they originally opened, but only if it is a teleX page; any other destination leads to the Inbox
>
> — `spec.md §5, AC-101, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `frontend/src/app/landing.ts` — `landAfterSignIn(createdAccount)`; use it from SCR-07 and SCR-08 (and T16's passkey sign-in).
- [ ] `frontend/src/pages/confirm-link/` — SCR-08 with every state; preview query must not retry on `410`.
- [ ] `previewSignInLink`, `redeemSignInLink` in `frontend/src/api/signIn.ts`; port `LoadState`.
- [ ] Copy in `messages.ts`; Vitest per state + landing rule.

## Edge cases

| Case | Behaviour |
|---|---|
| Preview OK, confirm after expiry | `refused-expired` on confirm (AC-35) |
| Page opened by a scanner, never clicked | only `previewSignInLink` was called (AC-86) |
| Remembered `/profile#sessions`, existing account | lands there |
| Remembered page but `createdAccount: true` | SCR-09, remembered page discarded (AC-34) |
| URL without fragment | `link-unusable` |

## Definition of Done

- [ ] Vitest covers every SCR-08 state and the landing rule.
- [ ] Works at 360 px (the "Continue as" button wraps) and 1280 px; tokens only; copy in `messages.ts`.
- [ ] `pnpm run check` clean.
- [ ] every Hard Rule inlined above still holds.
