## Summary

This PR ships E01: an Operator starts teleX with one command, and an Owner signs up and signs in. Sign-in uses an emailed Sign-in Link or a Sign-in Code, and a passkey is offered after the first sign-in. Owners can see and end their sessions and remove passkeys. Every new sign-in sends a "New sign-in to teleX" email.

The branch also carries step 0, the greenfield skeleton: 13 Modulith modules, CI, Compose, the Flyway baseline and the SPA served by Spring. `master` holds only the initial commit.

- Spec: [`docs/features/platform-skeleton/spec.md`](docs/features/platform-skeleton/spec.md)
- Changelog: [`docs/features/platform-skeleton/_ship/changelog.md`](docs/features/platform-skeleton/_ship/changelog.md)

## Acceptance criteria

- AC-33 — the one command brings up the sign-in page and a local mailbox ✓
- AC-34 — sign-up by link creates the account, offers a passkey and lands on the empty Inbox. The same address (case and +tag ignored) is the same account ✓
- AC-82 — a code typed on the laptop signs it in; the link from the same email then stops working ✓
- AC-35 — the link or code expires after 15 min, checked at confirm or code entry ✓
- AC-83 — an incomplete address is refused and no email is sent ✓
- AC-84 — a used link or code is refused with "already used" ✓
- AC-85 — after 5 wrong codes the email is void ✓
- AC-86 — a scanner that opens the link doesn't sign anyone in; the person's confirm still works ✓
- AC-103 — only the newest email for an address works ✓
- AC-104 — a sign-in in a browser that is already signed in replaces that browser's session ✓
- AC-89 — passkey creation, auto-naming, and passkey sign-in without typing an email ✓
- AC-90 — on an unsupported browser the passkey step explains this and continues ✓
- AC-105 — a cancelled or failed passkey creation stays on the step and adds nothing ✓
- AC-91 — "Not now" leads to the Inbox; the passkey step is never offered again ✓
- AC-92 — a removed passkey (including the last one) can no longer sign in ✓
- AC-93 — the sessions list shows each session; ending one sends that device to "Session ended" ✓
- AC-94 — "Sign out of all other sessions" leaves only this device ✓
- AC-95 — after Sign out, Back shows no data ✓
- AC-96 — a session ends after 30 days idle or 90 days from start; background refreshes don't count as activity ✓
- AC-97 — another Owner's sessions and passkeys are invisible and can't be changed ✓
- AC-98 — a "New sign-in to teleX" email for every new session except the one that creates the account ✓
- AC-100 — the empty Inbox shows "Connect Telegram" ✓
- AC-101 — a deep link goes through sign-in and back, for teleX pages only ✓
- AC-102 — "Page not found", and "teleX is unavailable" with a Retry that repeats the action ✓

## Design

- Spec: `docs/features/platform-skeleton/spec.md`
- Architecture: `docs/features/platform-skeleton/sad.md`
- Decisions: `docs/features/platform-skeleton/adr/` (ADR-0001…0006, all Accepted). Foundation ADRs are in `docs/adr/`.
- Data model + migration: `docs/features/platform-skeleton/data-model.md` (migrations `V202610021200`…`V202610021203`, `V202610021600`)
- API: `docs/features/platform-skeleton/contracts/openapi.yaml` (+ `events.md`)
- UX: `ux-flows.md`, `screens.md`
- Tests: `test-plan.md`
- Review: `_review/review-2026-10-02.md` (first pass CHANGES REQUESTED → fixes T21–T36 → re-review **PASS**)

## Tasks (SDD-Task trailers)

- Feature: T1–T20
  - d66a44c T1 promote identity migrations
  - 04cbd5d T2 identity domain primitives
  - f32e3f9 T3 mail module, Mailer port, SMTP adapter
  - 9996bda T4 Owner and Sign-in Session core
  - 7fad965 T5 issue Sign-in Grant, read link
  - 7cba7b0 T6 redeem link/code atomically
  - 7725761 T7 New sign-in email after commit
  - 8b77914 T8 security filter chain, cookie, CSRF, no-store
  - dc5916e T9 sign-in REST endpoints
  - b408b4f T10 sessions + /me
  - ae1789a T11 WebAuthn passkeys
  - a918763 T12 list/remove my passkeys
  - aa74f63 T13 SPA router, query client, system pages
  - a047326 T14 Sign in + Check your email pages
  - d3e9f3c T15 Confirm link page + landing rule
  - 442c1a2 T16 Create a passkey page + passkey sign-in
  - a632958 T17 PageFrame + empty Inbox
  - eaba18c T18 Profile and security page
  - c202618 T19 one-command install with Compose, Mailpit, smoke script
  - a9ab85d T20 Playwright e2e at 360/1280 px with axe and a virtual authenticator
- Review fixes, first pass: T21–T30
  - 1d1b3c7 T21
  - 27dd800, a876421 T23
  - 7273472 T22
  - 3cf56ce T24
  - 0afa2d5 T25
  - ef8aee1 T26
  - 66161ad T27
  - 8b46733 T28
  - 318686d, e60aa1d T29
  - 04f8857 T30
- Review fixes, second pass: T31–T36
  - a3c2f49 T31
  - 20d98fb T32
  - 3f7ff87 T33
  - c8fc639, 82850c2 T34
  - 1c03fdd T35
  - c8c40ae T36

## Verification

Run on 2026-10-02 at HEAD `7796e48`, on macOS with Docker Desktop.

- **Unit:** `./gradlew build` passed: 53 backend unit tests (including `ModularityTest` / `ApplicationModules.verify()`), plus frontend `pnpm run check` (tsc, ESLint, Prettier, 120 Vitest).
- **Integration:** `./gradlew integrationTest` passed: 96 tests on Testcontainers pgvector. They include `MigrationRollbackIT` (up → down → up) and `ContractValidator` checks against `openapi.yaml`.
- **Lint + vet:** detekt, ktlint (spotless), ESLint and `tsc --noEmit` are all clean.
- **e2e:** Playwright ran against `docker compose up` built from HEAD, with 23 passed and 1 skipped. The skip is the phone-only touch-target spec, which doesn't run in the desktop project. Phone (360 px) and desktop (1280 px) cover AC-34, 82, 89, 92, 93, 95, 98, 101 and 102 (404, 5xx, 10 s timeout), with axe scans and a virtual authenticator.
- **Ran the feature** over HTTP against the live stack:
  - **AC-33:** `docker compose up -d --build` from HEAD took 154 s. The sign-in page answered 200 four seconds after the app container started. Mailpit at :8025 received every email.
  - **AC-83:** `me@localhost` → 400 `validation-failed` / `email-incomplete`. Mailpit has 0 messages for that address.
  - **AC-103:** after a second email for the same address, the first email's code → 410 `sign-in-link-expired`. The second email's code → 200 `{"createdAccount":true}`.
  - **AC-84:** the same code again from a fresh browser → 410 `sign-in-link-used`.
  - **AC-85:** wrong codes 1–4 → 422, the 5th → 410. The correct code afterwards → 410 `sign-in-grant-void`.
  - **AC-97:** Owner B's session list has only B's own session. B deleting A's session id → 404 `not-found`, and A's `/api/v1/me` still answers 200.
- **Not verified here; to complete before merge (E01 DoD / spec §6):**
  - [ ] **Clean-machine timed install (≤ 5 min, not counting the first build).** The 154 s above used warm Docker layer and Gradle caches on a dev machine, so it is not the clean-machine measurement.
  - [ ] **Passkeys in real Safari (macOS) and Safari (iOS).** Only Chromium with a virtual authenticator ran.
  - [ ] **CI rejects a deliberate detekt/ktlint violation.** This needs the pushed branch and CI: push a throwaway commit with a violation, check that CI is red and merge is blocked, then drop the commit.

## Operational notes

- **Migrations:** Flyway applies 5 new migrations on startup (owner, sign-in grant, sign-in session, passkey tables, widened `user_entities.display_name`), on top of the step-0 baseline. Each has a `db/rollback/U…` script. Rolling back means reverting the deploy and applying the `U…` scripts in reverse version order, which drops all accounts, sessions and passkeys.
- **Config:**
  - `TELEX_PUBLIC_URL` sets email links, cookie `Secure` and the passkey RP id. Choose it once.
  - `TELEX_MAIL_*` holds SMTP settings; the default is the bundled Mailpit.
  - `TELEX_DB_PORT` sets the host port for Postgres.
- Don't expose an installation publicly before E26: registration is open, and there are no rate limits or anti-enumeration (spec §3, §8).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
