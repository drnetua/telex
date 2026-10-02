---
status: Draft
owner: "Anton Husiev"
reviewers: ["Tech Lead", "Security Lead"]
updated_at: "2026-10-02"
feature_size: "M"
---

# Spec — platform-skeleton

> **Glossary:** [CONTEXT](../../../CONTEXT.md) (repo root; no feature-level CONTEXT.md)
> **Reference module / docs / channels used:** `docs/docs/02-epics.md` §E01 + shared DoD · `docs/docs/03-product-spec.md` SCR-01, SCR-64, SCR-90, D-14, D-17, D-18 · `docs/docs/01-tech-spec.md` §Глосарій, §NFR · `docs/architecture-map.md` §Constraints · `docs/roadmap.md` step 1 · `docs/teleX-screens/Onb01-SignIn.html`, `docs/teleX-screens/Empty.html` · `docs/docs/design-system/README.md` §Content, §Accessibility.

## 1. Context

teleX is a self-hosted, multi-user web Telegram client. Before any Telegram or AI feature can exist, two people need a way in. The Operator has to get an installation running without hand configuration, and every Owner needs a teleX account of their own. Later epics make that account a gateway to the Owner's Telegram: from E02 on, whoever holds a teleX Sign-in Session can read the Owner's chats and send as them. So signing in has to be passwordless, low-friction on phone and laptop, and hard to take over silently.

Why now: the skeleton (step 0) has landed. It provides the 13 empty modules, CI, the database with migrations and the served web app, but nobody can sign in yet. Every other epic on the roadmap (E02 Telegram link, E06 app shell, E10 model profiles, E26 operator console) needs a signed-in Owner, so this is the single blocker for wave 3.

Committed approach — email first, with protections. An Owner signs up and signs in with an emailed Sign-in Link or its Sign-in Code. A Passkey is offered right after the first sign-in and becomes the fastest way back in, but it is never required. Three protections are kept proportionate to a course installation. A Sign-in Link only signs in after the person confirms on the page it opens, and the session starts in the browser where the link is confirmed or the code is typed. Every new sign-in triggers a "New sign-in to teleX" email. Competitive research found no verified product that combines open email sign-up, a skippable passkey prompt with an explained fallback for unsupported browsers, and per-device session control. Self-hosted tools are either passkey-only or email-only. From the same research, teleX adopts two lessons: a typed code next to the link for people who switch devices, and a first run that doesn't depend on an outside mail server. The sharpest failure mode ("the mailbox is the master key: whoever reads the Owner's email silently owns their teleX, and later their Telegram") is answered by the new-sign-in email, session revocation and a 90-day cap on every session; heavier defences (anti-enumeration, rate limits) are deliberately left out of E01. Success means the sign-in page is open within 5 minutes of the one command (not counting the first build of the teleX application), and a new Owner can go from there to the empty Inbox without help.

Traceability:
- Epic E01 features 1–4 (modules, CI, Compose, SPA served by the app) were delivered by the skeleton (step 0, `docs/features/_scaffold/`). This spec covers feature 5 (sign-up and sign-in), the one-command README (AC-33) and the E01 DoD checks (the deliberate-lint-violation CI check is a §6 row; passkeys in Chrome and Safari are a §6 row). AC-33, AC-34 and AC-35 and US-01 and US-41 keep their epic ids. New stories start at US-45 and new criteria at AC-82, after the highest ids in `02-epics.md`.
- Decision deviation: the passkey is optional (offered and skippable), while E01 AC-34 says the person "creates a passkey". The reason is that an Owner on a device without passkey support must not be stuck. The passkey step stays in the first-sign-in flow.
- Decision deviation: any passkey can be removed, including the last one, while SCR-64 says the last passkey can't be deleted. The reason is that email sign-in always remains, so the rule adds no security, and it would keep a passkey on a lost device alive.
- Decision deviation: the Owner definition is widened from "has linked Telegram" (tech spec) to "has a teleX account". The reason is that an Owner exists from sign-up, before any Linked Account.
- Decision deviation: SCR-90's "under maintenance" page is replaced by "teleX is unavailable". The reason is that a maintenance switch needs Operator controls, which arrive in E26.
- Decision deviation: AC-33's 5 minutes exclude the first build of the teleX application. The reason is that building from source on a clean machine can take 5 minutes by itself, and publishing a prebuilt image isn't an E01 goal.
- Decision deviation: SCR-90's "no access" page is not built in E01. Another Owner's records are hidden as if they don't exist (AC-97), and the first Operator-only page arrives in E26, which brings the "no access" page with it.
- Decision deviation: the size is M, while `02-epics.md` sizes E01 as S. The Sign-in Code, session control and the local mailbox took the scope from 3 epic ACs to 24 (reclassified after the critic pass; trimmed during clarify).
- Decision deviation: SCR-01 gains a Sign-in Code entry, which the mockup doesn't show.

## 2. Goals

- An Operator gets a working installation from one command and can complete the first sign-in without configuring anything.
- A new Owner reaches their empty Inbox from nothing but an email address, on phone or laptop, without ever choosing a password.
- An Owner can see every place they are signed in and cut off any of them, and hears about every new sign-in, so a leaked mailbox or lost device doesn't stay a silent takeover.

## 3. Non-goals

- Linking Telegram accounts. It is E02; this epic only shows the "Connect Telegram" step on the empty Inbox.
- The navigation shell, theme switcher and timezone on Profile and security. They are E06 (app-shell-responsive); this epic ships a minimal page frame.
- Operator tools and the Operator role itself: an admin UI, quotas, blocking Owners, a model catalog, and how the installation recognizes its Operator. They are E26; in E01 every person who signs up is an Owner, and the Operator is only the person who runs the one command.
- Passwords, social sign-in, or single sign-on with external identity providers. teleX is passwordless by product decision (tech spec §Стек).
- Changing an Owner's email, deleting an account, or Operator-assisted recovery of a lost mailbox. None of these is needed to reach the E01 value, and each needs its own consent and audit design.
- Hiding which addresses have accounts and rate-limiting sign-in emails. They protect a public installation; E01 targets a local course installation (see §8 on public exposure).
- Renaming a Passkey. Names are generated from the browser and device, which is enough to tell passkeys apart in E01.
- The "no access" system page from SCR-90. There is no Operator-only page before E26; the page ships with the first one.
- A managed maintenance mode that the Operator switches on ("teleX is under maintenance" in SCR-90). It needs an Operator control, and those arrive in E26; E01 only shows "teleX is unavailable" when the app doesn't respond.
- A production deployment guide (public domain, HTTPS, real mail provider hardening). The one command targets a local machine; the README only names the settings a public install must change.

## 4. User stories

### US-41: Start teleX with one command

**As an** Operator
**I want** to start a complete teleX installation with one command from the README
**So that** the installation needs no manual setup before the first person can sign in

### US-01: Sign in without a password

**As an** Owner
**I want** to sign up and sign in with just my email address, using the link or the code from the email
**So that** I never create or remember a password, on whichever device I read my email

### US-45: Use a passkey

**As an** Owner
**I want** to create a Passkey after my first sign-in (or skip it) and use it to sign in later
**So that** signing back in takes one touch instead of a trip to my mailbox

### US-46: Control where I am signed in

**As an** Owner
**I want** to see my active Sign-in Sessions and Passkeys, end any session, and sign out
**So that** a lost device or a stolen session stops working when I say so

### US-47: Hear about new sign-ins

**As an** Owner
**I want** an email every time someone signs in to my account in a new session
**So that** I notice a takeover through my mailbox before it does damage

### US-49: Understand dead ends

**As an** Owner
**I want** a clear system page when a page doesn't exist, my session ended, or teleX can't be reached
**So that** I always know what happened and have one action to get back

## 5. Acceptance criteria

### AC-33 (US-41) — happy

**Given** a clean machine that has only Docker and a copy of the repository
**When** the Operator runs the one command from the README
**Then** within 5 minutes (not counting the first build of the teleX application) the address named in the README, opened by the Operator in their browser, shows the sign-in page; the README names a local mailbox page where every email teleX sends shows up, and the Operator can then complete a first sign-in with the email from that mailbox (the sign-in itself is outside the 5 minutes)

### AC-34 (US-01) — happy

**Given** a person whose email address has no teleX account
**When** they enter the address on the sign-in page, open the Sign-in Link from the email and confirm "Continue as <address>"
**Then** teleX creates their Owner account, signs them in in that browser, offers to create a Passkey, and afterwards shows the empty Inbox with the single step "Connect Telegram"; signing in again with the same address opens the same account, never a second one. Two addresses are the same when they match after ignoring letter case and any "+tag" before the @ (so `Anton+work@Mail.com` and `anton@mail.com` are one account). A sign-in email goes to the address exactly as typed that time; the "New sign-in to teleX" email goes to the address the account was created with

### AC-82 (US-01) — happy

**Given** a person who asked for a sign-in email on their laptop and reads the email on their phone
**When** they type the Sign-in Code from the email on the laptop's "Check your email" page
**Then** the laptop is signed in, and the Sign-in Link from the same email no longer works

### AC-35 (US-01) — error

**Given** a sign-in email sent more than 15 minutes ago
**When** the person opens its Sign-in Link, confirms "Continue as <address>" on a link page opened earlier, or types its Sign-in Code
**Then** sign-in is refused, because the 15 minutes are checked at the confirm or the typed code, not only when the link is opened; they see that the link has expired and a "Send a new link" action that emails a fresh link to the same address

### AC-83 (US-01) — error

**Given** the sign-in page
**When** the person submits something that isn't a valid email address
**Then** no email is sent, and the field tells them to enter a complete email address, with a name, an @ sign and a domain that contains a dot (for example `me@example.com`; `me@localhost` is refused)

### AC-84 (US-01) — domain invariant

**Given** a Sign-in Link or Sign-in Code that has already been used to sign in
**When** anyone opens that link or types that code again
**Then** sign-in is refused with "This link was already used", and a "Send a new link" action is offered

### AC-85 (US-01) — domain invariant

**Given** a sign-in email whose Sign-in Code has been typed wrong 5 times
**When** the person tries a 6th code, or opens the link from that email
**Then** sign-in is refused, the person is told the code is no longer valid, and they are asked to request a new email

### AC-86 (US-01) — domain invariant

**Given** a Sign-in Link opened by a mail scanner or a link preview that never confirms
**When** the person later opens the same link and confirms "Continue as <address>" within 15 minutes
**Then** they are signed in normally, because a link signs in only after the confirm step

### AC-103 (US-01) — domain invariant

**Given** a person who asked for a sign-in email and then asked for another one for the same address
**When** they open the link or type the code from the earlier email
**Then** sign-in is refused as expired, with a "Send a new link" action; only the newest email for an address works, and a code typed on a "Check your email" page counts only against the email that this page asked for

### AC-104 (US-01) — domain invariant

**Given** a browser that is already signed in, as the same Owner or as another one
**When** a Sign-in Link is confirmed or a Sign-in Code is typed in that browser
**Then** the existing Sign-in Session in that browser ends and a new one starts for the Owner the email was sent to, with the "New sign-in to teleX" email per AC-98

### AC-89 (US-45) — happy

**Given** an Owner on a passkey-capable browser who has just signed in for the first time
**When** they accept "Create a passkey" and confirm with their device
**Then** the passkey is listed in Profile and security, named automatically after its browser and device (for example "Safari on iPhone"), with its creation date and its last-used date ("Never used" until first use), and their next sign-in on that device completes with "Sign in with a passkey" on the sign-in page and the device check alone, without typing an email address

### AC-90 (US-45) — error

**Given** an Owner whose browser can't create passkeys
**When** the passkey step appears after their first sign-in
**Then** it explains that this browser doesn't support passkeys and that they can add one later from another device, and it continues to the Inbox; sign-in by email keeps working

### AC-105 (US-45) — error

**Given** an Owner on a passkey-capable browser on the passkey step after their first sign-in
**When** they cancel the device check, or creating the passkey fails
**Then** they stay on the passkey step, see that no passkey was created, and can try again or choose "Not now"; no passkey is added to the list

### AC-91 (US-45) — happy

**Given** an Owner on the passkey step after their first sign-in
**When** they choose "Not now"
**Then** they land on the Inbox, and Profile and security shows "No passkeys yet" with an "Add a passkey" action; the passkey step only follows the sign-in that creates the account and is not offered again on later sign-ins, on any device

### AC-92 (US-46) — happy

**Given** an Owner with one or more Passkeys, including their only one
**When** they remove a passkey in Profile and security and confirm
**Then** the passkey disappears from the list and can no longer sign them in, and sign-in by email keeps working; Sign-in Sessions already open on any device stay open, and the confirm step reminds the Owner to end a lost device's session in the sessions list (AC-93)

### AC-93 (US-46) — happy

**Given** an Owner signed in on a laptop and a phone
**When** they open Profile and security on the laptop and end the phone's session
**Then** the list shows each session's browser, device type and last activity, with the current one marked "This device", and the phone's next action lands on the "Session ended" page

### AC-94 (US-46) — happy

**Given** an Owner signed in on several devices
**When** they choose "Sign out of all other sessions"
**Then** every session except the current one ends, and the list shows only "This device"

### AC-95 (US-46) — happy

**Given** a signed-in Owner
**When** they choose "Sign out"
**Then** they land on the sign-in page, and going back in the browser does not show any of their data

### AC-96 (US-46) — domain invariant

**Given** a Sign-in Session that has been idle for 30 days (activity means a page the Owner opens or an action they take; background refreshes of an open tab don't count), or that started 90 days ago no matter how active it is
**When** the Owner next opens teleX in that browser
**Then** they see the "Session ended" page with a "Sign in again" action

### AC-97 (US-46) — authorization

**Given** two Owners on the same installation
**When** one Owner tries to see, end or remove the other Owner's Sign-in Session or Passkey
**Then** nothing changes, and it looks as if that session or passkey doesn't exist; each Owner only ever sees their own

### AC-98 (US-47) — happy

**Given** an Owner who already has an account
**When** a new Sign-in Session starts for them by link, code or passkey
**Then** they receive a "New sign-in to teleX" email naming the browser, device type and time (in the time zone of the browser that signed in, with the zone's name, and in UTC), with a link to the sessions list in Profile and security; no such email is sent for the sign-in that creates the account

### AC-100 (US-01) — cross-context

**Given** a signed-in Owner who has no Linked Account
**When** they open the Inbox
**Then** they see the empty Inbox with the single step "Connect Telegram"; the step stays until the Owner has at least one Linked Account

### AC-101 (US-49) — error

**Given** a signed-out person who opens a link to a teleX page
**When** the page needs a Sign-in Session
**Then** they see the sign-in page, and after signing in they land on the page they originally opened, but only if it is a teleX page; any other destination leads to the Inbox

### AC-102 (US-49) — error

**Given** a signed-in Owner
**When** they open an address that doesn't exist in teleX, or teleX stops responding while they use it (an action gets no answer within 10 seconds, or teleX answers with a server failure)
**Then** they see the matching system page ("Page not found" with a "Go to Inbox" action, or "teleX is unavailable" with a "Retry" action that repeats the action); "teleX is unavailable" can only appear in a teleX tab that has already loaded, and opening teleX from scratch while it is down shows the browser's own error

## 6. Non-functional requirements

| Aspect | Target | Measurement |
|---|---|---|
| One-command install to working sign-in page | ≤ 5 min from the command to the sign-in page on a clean machine with Docker and ≥ 50 Mbit/s, not counting the first build of the teleX application image | manual timed run on a clean machine, recorded in the E01 pull request |
| Sign-in Link lifetime | 15 min, single use | integration test with a controlled clock |
| Sign-in Code guessing | 6 digits, ≤ 5 wrong attempts per email, then void | integration test |
| Sign-in Session lifetime | ends after 30 days idle or 90 days from start | integration test with a controlled clock |
| Passkey browser coverage | sign-up, passkey creation and passkey sign-in work in current Chrome, Safari (macOS) and Safari (iOS) | Playwright virtual authenticator (Chromium) + manual check on Safari and iOS per E01 DoD |
| Responsive + accessible | every screen in this spec works at 360 px and 1280 px and meets WCAG 2.2 AA | Playwright at both widths + automated accessibility scan with 0 violations |
| Quality gate | a pull request with a deliberate detekt or ktlint violation fails CI and can't be merged | one-off check recorded in the E01 pull request (E01 DoD) |
| Availability | N/A — self-hosted single instance, no SLO in E01 | — |

## 6.1 Security / privacy

- **Data classification:** confidential. A teleX account becomes the key to an Owner's Telegram from E02 on.
- **Personal data touched:** Owner email address; passkey public credential with its name and dates; per session, the browser, device type and start and last-activity times.
- **AuthZ/AuthN impact:** every page and action except the sign-in pages, the system pages and health requires a live Sign-in Session; sessions, passkeys and profile data are only ever looked up among the caller's own records (AC-97).
- **Secrets:** a Sign-in Link, a Sign-in Code or a session key is never stored, shown back or logged in readable form.
- **Security review:** covered by the regular `/sdd:review`; no separate security review for E01.

## 7. Metrics / KPIs

- **Sign-up completion** (sign-in emails sent to new addresses → Owners who reach the Inbox; counted from the app's own sign-in events, with no content and no email addresses) — baseline: 0 (no sign-up exists); target: ≥ 90% within the first 30 days of real use on the course installation.
- **Passkey adoption** (Owners with ≥ 1 Passkey ÷ all Owners; counted from the app's own records) — baseline: 0; target: ≥ 50% within 30 days of each Owner's first sign-in.
- **Install-to-sign-in-page time** (one command → sign-in page open, not counting the first build of the teleX application image; measured by the manual timed run) — baseline: not possible today; target: ≤ 5 min on the run recorded when the epic ships.

## 8. Open questions

- [ ] Where does the "Connect Telegram" step lead before E02 ships? Default now: the step is shown and its action opens a short "Telegram linking is coming next" note, without a stub page in the E02 flow. — owner: Anton Husiev (PM), due: before `/sdd:design platform-skeleton`
- [ ] Should registration stay open once an installation is reachable from the internet, before E26 quotas exist? Default now: open; the README warns not to expose it publicly until E26. — owner: Anton Husiev (PM), due: before `/sdd:specify operator-console`
- [ ] How does an Owner who has lost their mailbox recover access (email change, Operator-assisted recovery)? Default now: not possible; out of scope for E01. — owner: Anton Husiev (PM), due: before `/sdd:specify operator-console`
