# Changelog — platform-skeleton

## platform-skeleton — passwordless sign-up and sign-in, passkeys, session control and a one-command install

**What:** teleX can now be started with one command, and people can get in. `docker compose up` brings up the app, Postgres + pgvector and a local mailbox (Mailpit). A person enters an email address and gets a Sign-in Link and a 6-digit Sign-in Code. The first confirmed sign-in creates their Owner account. After that first sign-in they are offered a Passkey, which they can skip. The empty Inbox shows a single "Connect Telegram" step. Profile and security lists the Owner's Passkeys and Sign-in Sessions. From there the Owner can remove any passkey, end any session, or sign out of all other sessions. Every new sign-in after account creation sends a "New sign-in to teleX" email. Dead ends get system pages: "Page not found", "Session ended" and "teleX is unavailable" with a working Retry.

**Why:** every later epic needs a signed-in Owner, and from E02 on a teleX session opens the Owner's Telegram. So sign-in has to be passwordless and work on both phone and laptop, and a takeover must not go unnoticed ([spec](../spec.md) §1, §2). Key decisions:

- [ADR-0001](../adr/0001-keep-sign-in-sessions-in-an-identity-owned-table-behind-an-opaque-cookie.md): sessions live in an `identity`-owned table behind an opaque cookie, so they can be listed and revoked.
- [ADR-0002](../adr/0002-use-spring-security-webauthn-for-passkeys.md): passkeys use Spring Security WebAuthn.
- [ADR-0003](../adr/0003-redeem-sign-in-link-and-code-as-one-hashed-single-use-grant.md): the link and the code are one hashed, single-use Sign-in Grant. Only the newest grant for an address is live.
- [ADR-0004](../adr/0004-send-email-through-a-new-mail-integration-module.md): email goes through a new `mail` integration module.
- [ADR-0005](../adr/0005-count-session-activity-only-from-requests-the-spa-does-not-mark-as-background.md): background refreshes don't keep a session alive.
- [ADR-0006](../adr/0006-derive-links-cookie-security-and-passkey-rp-id-from-one-public-url.md): one `TELEX_PUBLIC_URL` drives email links, cookie security and the passkey RP id.

**How to use:** run `docker compose up` and open http://localhost:8080. Enter an email, then open http://localhost:8025 and use the link or the code from the email (see the [README](../../../../README.md)). The API is in [openapi.yaml](../contracts/openapi.yaml):

- Sign-in: `POST /api/v1/sign-in/grants` → `GET /api/v1/sign-in/link/preview` + `POST /api/v1/sign-in/link/redeem`, or `POST /api/v1/sign-in/grants/{grantId}/code`.
- Account: `GET /api/v1/me`.
- Sessions: `GET/DELETE /api/v1/sessions[/{id}]`, `POST /api/v1/sessions/end-others`.
- Passkeys: `GET/DELETE /api/v1/passkeys[/{id}]`, plus the standard WebAuthn endpoints (`/webauthn/register*`, `/webauthn/authenticate/options`, `/login/webauthn`).

All errors are RFC 9457 problems with the contract's `code` values.

**Operational notes:**
- Migrations: five Flyway migrations are applied automatically on startup:
  - `V202610021200__create_owner`
  - `V202610021201__create_sign_in_grant`
  - `V202610021202__create_sign_in_session`
  - `V202610021203__create_passkey_tables`
  - `V202610021600__widen_user_entity_display_name`

  Each has a matching `db/rollback/U…` script. `MigrationRollbackIT` proves up → down → up for all of them.
- Config: `TELEX_PUBLIC_URL` sets the public address. Choose it once, because passkeys are bound to its host. SMTP is set with `TELEX_MAIL_HOST`/`_PORT`/`_USERNAME`/`_PASSWORD`/`_FROM`; it defaults to the bundled Mailpit. If port 5432 is taken, set `TELEX_DB_PORT`. Don't expose an installation publicly before E26: registration is open, with no rate limits and no protection against address enumeration (spec §3, §8).
- Rollback: revert the deploy, then apply the `U…` scripts in reverse version order. This drops all Owners, sessions and passkeys.

**Acceptance criteria delivered:** AC-33, AC-34, AC-82, AC-35, AC-83, AC-84, AC-85, AC-86, AC-103, AC-104, AC-89, AC-90, AC-105, AC-91, AC-92, AC-93, AC-94, AC-95, AC-96, AC-97, AC-98, AC-100, AC-101, AC-102 (all 24 in spec §5).

**Spec deviations (recorded in spec §1):**
- The passkey is optional.
- The last passkey can be removed.
- An Owner exists from sign-up, before any Telegram account is linked.
- "teleX is unavailable" replaces "under maintenance".
- The "no access" page is deferred to E26.
- The size is M instead of S.
