---
id: T14
title: "Build Sign in (SCR-01, email part) and Check your email (SCR-07) with the Sign-in Code"
layer: "ui"
deps: ["T13"]
blocks: ["T15", "T19"]
acs: ["AC-83", "AC-82", "AC-35", "AC-84", "AC-85", "AC-103"]
files_hint: ["frontend/src/pages/sign-in/", "frontend/src/pages/check-email/", "frontend/src/pages/auth/", "frontend/src/components/CodeInput/", "frontend/src/api/signIn.ts", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T14 — Build Sign in (SCR-01, email part) and Check your email (SCR-07) with the Sign-in Code

## Place in the sequence

- **Blocked by:** T13 — Set up the SPA router, query client and fetch client with failure routing, and build the system pages SCR-91, SCR-92 and SCR-93 · **Blocks:** T15 — Build Confirm sign-in link (SCR-08) and the landing-after-sign-in rule, T19 — Make teleX start with one command: Dockerfile, compose with app and Mailpit, README and a smoke script · **Wave:** 2 — needs the router, fetch client and ported components (T13).
- **Lane:** owns `pages/sign-in/` (T16 adds the passkey button there — T16 depends on this task) and the shared auth layout + refusal card in `pages/auth/` reused by T15.

## Why (user story)

> **As an** Owner
> **I want** to sign up and sign in with just my email address, using the link or the code from the email
> **So that** I never create or remember a password, on whichever device I read my email
>
> — `spec.md §4, US-01, verbatim` · full text: [spec.md](../spec.md)

This task builds the two pages where an Owner asks for a sign-in email and types the code from it.

## Inlined context

> **Auth layout.** SCR-01, SCR-07, SCR-08 and SCR-09 use Tabler `page-center` with a logo asset 96 px above a `card` that is at most 420 px wide, padded `space-4`, with `radius-lg` and `shadow-sm`, as in `Onb01-SignIn`. […] On phone the card fills the width inside a 16 px gutter and uses padding `space-3`.
>
> — `screens.md §Shared conventions, abridged` · full text: [screens.md](../screens.md)

**SCR-01 states (email part; passkey states are T16):**
- `default` — h1 "Sign in to teleX", text-secondary "Your Telegram, with assistants you stay in charge of.", Tabler `form-control` (label "Email", `type=email`, `autocomplete=email`), `Button` primary block "Email me a sign-in link", divider "or", (passkey button slot — T16), `small` "No passwords. The link works once and expires in 15 minutes."
- `validation` — client rule + `400 validation-failed`/`email-incomplete`: `form-control is-invalid` + `invalid-feedback` with `alert-circle`: "Enter a complete email address, like me@example.com." No request when the client rule fails.
- `submitting` — busy `Button` ("Sending email"), field read-only. `error` → shared failure routing. `success` → `201` → SCR-07 for this address.

**SCR-07 states:**
- `default` — holds this email's `grantId` + `<address>` (router state); h1 "Check your email", "We sent a sign-in link and a 6-digit code to `<address>`. Open the link, or type the code here.", `CodeInput` (6 digits, auto-advance, paste fills all, one-time-code autofill), `Button` primary "Sign in", `Button` ghost "Send a new link", `small` "The link and the code work once and expire in 15 minutes."
- `validation` — fewer than 6 digits or `400 code-format`: "Enter the 6-digit code from the email."
- `submitting` — busy "Signing in". `wrong-code` — `422` + `attemptsLeft`: `CodeInput state="invalid"` + "That code is not right. {n} tries left." ("1 try left"), focus to first digit.
- `refused-expired` / `refused-used` / `refused-void` — card replaced: h1 "This link has expired" (icon `clock`) / "This link was already used" (icon `ban`) / "This code is no longer valid" (icon `ban`, "Request a new email to `<address>` to sign in."), `EmptyState kind="blocked"` "Send a new link to `<address>` to sign in.", action "Send a new link".
- `resending` (busy "Sending email") → `resent` (fresh SCR-07 for the new grant + `alert` info-subtle "We sent a new email to `<address>`. Only the newest email works.") · `no-grant` → redirect to SCR-01 · `error` → shared routing · `success` → `createdAccount` ? SCR-09 : remembered page or SCR-10 (landing helper from T15 — until it lands, navigate via `takeRememberedDestination()` from T13).

— `screens.md §SCR-01 + §SCR-07, abridged` · full text: [screens.md](../screens.md) · wireframes W-01a/b, W-07a–d

**Reuse:** T13 `Button` (busy), `EmptyState`, `Icon`; port `CodeInput` from `docs/docs/design-system/components/CodeInput/` (`state="input" | "invalid"`; `limited`/`resendIn` unused); Tabler `form-control`, `invalid-feedback`, `alert`. Build the refusal card as a shared `pages/auth/RefusalCard` — T15 reuses it on SCR-08.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

- `requestSignInEmail` `POST /api/v1/sign-in/grants` `{email}` → `201 {grantId, email}` · `400` field `email`/`email-incomplete` · `503`/`403` → shared routing.
- `redeemSignInCode` `POST /api/v1/sign-in/grants/{grantId}/code` `{code}` + header `X-Telex-Time-Zone: Intl.DateTimeFormat().resolvedOptions().timeZone` → `200 {createdAccount}` · `400 code-format` · `410` `sign-in-link-expired` / `sign-in-link-used` / `sign-in-grant-void` (+`email`) · `422 sign-in-code-wrong` + `attemptsLeft`.

— `contracts/openapi.yaml, operationIds requestSignInEmail + redeemSignInCode, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-83 — error

> **Given** the sign-in page
> **When** the person submits something that isn't a valid email address
> **Then** no email is sent, and the field tells them to enter a complete email address, with a name, an @ sign and a domain that contains a dot (for example `me@example.com`; `me@localhost` is refused)
>
> — `spec.md §5, AC-83, verbatim` · full text: [spec.md](../spec.md)

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

## Checklist

- [ ] `frontend/src/pages/auth/` — `AuthLayout` (logo, card) and `RefusalCard`.
- [ ] `frontend/src/pages/sign-in/` — SCR-01 email form with the openapi email rule client-side.
- [ ] `frontend/src/pages/check-email/` — SCR-07 with all states; grant id from router state only (no storage, screens.md §Noted gaps 1).
- [ ] `frontend/src/components/CodeInput/` — port.
- [ ] `frontend/src/api/signIn.ts` — `requestSignInEmail`, `redeemSignInCode` (time-zone header).
- [ ] Copy into `frontend/src/messages.ts`.
- [ ] Vitest per page.

## Edge cases

| Case | Behaviour |
|---|---|
| `me@localhost` | `validation` state, no request (AC-83) |
| Paste "482019" into the first digit | all six filled |
| `attemptsLeft: 1` | "1 try left" |
| 5th wrong code → `410 sign-in-grant-void` | `refused-void` (AC-85) |
| Reload of SCR-07 | `no-grant` → SCR-01; the email's link still works |
| "Send a new link" from a refused state | `requestSignInEmail` with the same address → `resent` |
| `503 mail-unavailable` on resend | SCR-93 |

## Definition of Done

- [ ] Vitest covers every SCR-01 email state and every SCR-07 state listed above.
- [ ] Works at 360 px and 1280 px; status never by color alone (icon + words); tokens only; all copy in `messages.ts`.
- [ ] `pnpm run check` clean.
- [ ] every Hard Rule inlined above still holds.
