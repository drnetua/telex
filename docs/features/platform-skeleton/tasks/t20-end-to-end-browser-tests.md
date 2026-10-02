---
id: T20
title: "Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator"
layer: "tests"
deps: ["T7", "T10", "T12", "T15", "T16", "T18", "T19"]
blocks: []
acs: ["AC-34", "AC-82", "AC-89", "AC-93", "AC-95", "AC-98", "AC-101", "AC-102"]
files_hint: ["e2e/", "package.json", "pnpm-workspace.yaml", ".github/workflows/ci.yml"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T20 — Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator

## Place in the sequence

- **Blocked by:** T7 — Send the "New sign-in to teleX" email from the SignInSessionStarted event after commit, T10 — List and end my Sign-in Sessions and answer "who am I", Owner-scoped end to end, T12 — List and remove my Passkeys, filtered by my WebAuthn user entity, T15 — Build Confirm sign-in link (SCR-08) and the landing-after-sign-in rule, T16 — Build Create a passkey (SCR-09) and "Sign in with a passkey" on SCR-01, T18 — Build Profile and security (SCR-64): passkeys card and sign-in sessions card, T19 — Make teleX start with one command: Dockerfile, compose with app and Mailpit, README and a smoke script · **Blocks:** — · **Wave:** 6 — last: needs every screen and endpoint plus the compose stack (T19).
- **Lane:** own lane (`e2e/`, CI workflow).

## Why (user story)

> **As an** Owner
> **I want** to sign up and sign in with just my email address, using the link or the code from the email
> **So that** I never create or remember a password, on whichever device I read my email
>
> — `spec.md §4, US-01, verbatim` · full text: [spec.md](../spec.md)

This task proves the whole front door works in a real browser at phone and desktop widths, with passkeys and accessibility checked by machine.

## Inlined context

> | Passkey browser coverage | sign-up, passkey creation and passkey sign-in work in current Chrome, Safari (macOS) and Safari (iOS) | Playwright virtual authenticator (Chromium) + manual check on Safari and iOS per E01 DoD |
> | Responsive + accessible | every screen in this spec works at 360 px and 1280 px and meets WCAG 2.2 AA | Playwright at both widths + automated accessibility scan with 0 violations |
>
> — `spec.md §6, NFR rows Passkey browser coverage + Responsive + accessible, verbatim` · full text: [spec.md](../spec.md)

> *QG-3a:* **When:** any screen of this feature (SCR-01, 07, 08, 09, 10, 64, 91, 92, 93) is rendered in any of its states. **How verify:** Playwright runs each screen at both widths, plus an automated accessibility scan (axe) with 0 violations.
> *QG-3b:* **How verify:** Playwright with a CDP virtual authenticator (Chromium) in CI, plus a manual check on Safari macOS and iOS against `localhost`, or HTTPS through Cloudflare, recorded per the E01 DoD.
>
> — `sad.md §10, QG-3a + QG-3b, abridged` · full text: [sad.md](../sad.md)

> **CI:** `.github/workflows/ci.yml` runs `./gradlew build integrationTest` on JDK 25 + pnpm.
>
> — `CLAUDE.md §Layout and code conventions, CI, verbatim` · full text: [CLAUDE.md](../../../../CLAUDE.md)

**Route table (T13):** `/sign-in` · `/sign-in/check-email` · `/sign-in/link#<token>` · `/welcome/passkey` · `/inbox` · `/profile#sessions` · `/session-ended` · unknown → SCR-91. **Emails:** read through Mailpit's HTTP API (`http://localhost:8025/api/v1/…`), addresses on `example.test` only (data-model §Test fixtures). **Manual part (not automated here):** Safari macOS + iOS passkey check, recorded in the E01 PR.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — exercises the whole `contracts/openapi.yaml` surface through the UI; intercepts one request with `page.route` to force a `503`/timeout for SCR-93.

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

### AC-89 — happy

> **Given** an Owner on a passkey-capable browser who has just signed in for the first time
> **When** they accept "Create a passkey" and confirm with their device
> **Then** the passkey is listed in Profile and security, named automatically after its browser and device (for example "Safari on iPhone"), with its creation date and its last-used date ("Never used" until first use), and their next sign-in on that device completes with "Sign in with a passkey" on the sign-in page and the device check alone, without typing an email address
>
> — `spec.md §5, AC-89, verbatim` · full text: [spec.md](../spec.md)

### AC-93 — happy

> **Given** an Owner signed in on a laptop and a phone
> **When** they open Profile and security on the laptop and end the phone's session
> **Then** the list shows each session's browser, device type and last activity, with the current one marked "This device", and the phone's next action lands on the "Session ended" page
>
> — `spec.md §5, AC-93, verbatim` · full text: [spec.md](../spec.md)

### AC-95 — happy

> **Given** a signed-in Owner
> **When** they choose "Sign out"
> **Then** they land on the sign-in page, and going back in the browser does not show any of their data
>
> — `spec.md §5, AC-95, verbatim` · full text: [spec.md](../spec.md)

### AC-98 — happy

> **Given** an Owner who already has an account
> **When** a new Sign-in Session starts for them by link, code or passkey
> **Then** they receive a "New sign-in to teleX" email naming the browser, device type and time (in the time zone of the browser that signed in, with the zone's name, and in UTC), with a link to the sessions list in Profile and security; no such email is sent for the sign-in that creates the account
>
> — `spec.md §5, AC-98, verbatim` · full text: [spec.md](../spec.md)

### AC-101 — error

> **Given** a signed-out person who opens a link to a teleX page
> **When** the page needs a Sign-in Session
> **Then** they see the sign-in page, and after signing in they land on the page they originally opened, but only if it is a teleX page; any other destination leads to the Inbox
>
> — `spec.md §5, AC-101, verbatim` · full text: [spec.md](../spec.md)

### AC-102 — error

> **Given** a signed-in Owner
> **When** they open an address that doesn't exist in teleX, or teleX stops responding while they use it (an action gets no answer within 10 seconds, or teleX answers with a server failure)
> **Then** they see the matching system page ("Page not found" with a "Go to Inbox" action, or "teleX is unavailable" with a "Retry" action that repeats the action); "teleX is unavailable" can only appear in a teleX tab that has already loaded, and opening teleX from scratch while it is down shows the browser's own error
>
> — `spec.md §5, AC-102, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `e2e/` workspace package (`@playwright/test`, `@axe-core/playwright`), registered in `pnpm-workspace.yaml`; projects `phone` (360×800) and `desktop` (1280×800), Chromium.
- [ ] Mailpit helper: wait for the newest message to an address, extract the link and the code.
- [ ] Specs: sign-up by link (→ SCR-09 → Not now → SCR-10), sign-in by code on a second context (AC-82), passkey create + sign-in with a CDP virtual authenticator (AC-89), new-sign-in email arrives for a non-creating sign-in (AC-98), end the other context's session → it lands on SCR-92 (AC-93), sign out + Back (AC-95), protected deep link → sign in → back there (AC-101), unknown address → SCR-91 and forced failure → SCR-93 → Retry (AC-102).
- [ ] axe scan on each screen state visited; fail on any violation.
- [ ] `.github/workflows/ci.yml` — job that runs `docker compose up -d --wait` then the e2e suite.

## Edge cases

| Case | Behaviour |
|---|---|
| Mailpit holds emails from earlier runs | each test uses a unique `…@example.test` address |
| Virtual authenticator unavailable (non-Chromium) | passkey specs run on Chromium only; manual Safari check documented |
| Flaky timing on cold start | wait on the sign-in page responding, not on sleeps |

## Definition of Done

- [ ] `pnpm --filter e2e test` passes locally against `docker compose up`, both projects.
- [ ] axe: 0 violations on SCR-01, 07, 08, 09, 10, 64, 91, 92, 93.
- [ ] CI runs the suite on every PR.
- [ ] every Hard Rule inlined above still holds.
