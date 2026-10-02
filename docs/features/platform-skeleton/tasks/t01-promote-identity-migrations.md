---
id: T1
title: "Promote the four staged identity migrations into the live Flyway tree"
layer: "migration"
deps: []
blocks: ["T4", "T5"]
acs: ["AC-34", "AC-85", "AC-103"]
files_hint: ["docs/features/platform-skeleton/migrations/01_create_owner.up.sql", "docs/features/platform-skeleton/migrations/01_create_owner.down.sql", "docs/features/platform-skeleton/migrations/02_create_sign_in_grant.up.sql", "docs/features/platform-skeleton/migrations/02_create_sign_in_grant.down.sql", "docs/features/platform-skeleton/migrations/03_create_sign_in_session.up.sql", "docs/features/platform-skeleton/migrations/03_create_sign_in_session.down.sql", "docs/features/platform-skeleton/migrations/04_create_passkey_tables.up.sql", "docs/features/platform-skeleton/migrations/04_create_passkey_tables.down.sql"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T1 — Promote the four staged identity migrations into the live Flyway tree

## Place in the sequence

- **Blocked by:** — · **Blocks:** T4 — Build the Owner and Sign-in Session core: start, resolve with the 30/90-day rules, end by key, and the SignInSessionStarted event, T5 — Issue a Sign-in Grant with its email and read a Sign-in Link without redeeming it · **Wave:** 1 — no upstream code needed; starts in parallel with T2, T3 and T13.
- **Lane:** `layer: migration` — serialized by `implement` (ordered migration sequence); no other task touches these files.

## Why (user story)

> **As an** Owner
> **I want** to sign up and sign in with just my email address, using the link or the code from the email
> **So that** I never create or remember a password, on whichever device I read my email
>
> — `spec.md §4, US-01, verbatim` · full text: [spec.md](../spec.md)

This task lays down the tables every sign-in rule is enforced against: one Owner per canonical address, one grant per sign-in email, one session per browser, and Spring's passkey tables.

## Inlined context

> **Migrations:** `backend/app/src/main/resources/db/migration/V<yyyyMMddHHmm>__<name>.sql`, each with a rollback `db/rollback/U<same-version>__<name>.sql`; `MigrationRollbackIT` applies up → down → up for all of them. Feature migrations are staged by `/sdd:data-model` in `docs/features/<slug>/migrations/` and promoted by `implement`.
>
> — `CLAUDE.md §Layout and code conventions, Migrations, verbatim` · full text: [CLAUDE.md](../../../../CLAUDE.md)

> All tables belong to the `identity` module. `web` and `mail` own no tables. The Modulith `event_publication` table (baseline) carries `SignInSessionStarted` and needs nothing new.
> - Time: `TIMESTAMP WITH TIME ZONE`, written from the injectable `Clock`. No `DEFAULT now()`, so tests with a fixed clock control every timestamp.
> - Secrets: SHA-256 digests stored as 32-byte `BYTEA`; plaintext never stored (spec §6.1).
> - Constraints: NOT NULL / UNIQUE / FK, plus light `CHECK`s on counters, hash lengths, enum-like values and time order (user decision, 2026-10-02). The rules are still enforced and tested in Kotlin.
>
> — `data-model.md §Conventions applied, abridged` · full text: [data-model.md](../data-model.md)

> Shape follows `spring-security-web` 7.1.1 `user-entities-schema.sql` and `user-credentials-schema-postgres.sql`. […] The column names, nullability and the rest of the types are unchanged, so Spring's JDBC repositories work without a custom row mapper.
>
> — `data-model.md §Aggregate: Passkey, abridged` · full text: [data-model.md](../data-model.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

Staged pairs to promote, in this order (FKs: `sign_in_session → owner`, `user_credentials → user_entities`). Suggested live versions — keep the order, any later timestamp works:

| Staged pair | Live up / rollback |
|---|---|
| `migrations/01_create_owner.{up,down}.sql` | `V202610021200__create_owner.sql` / `U202610021200__create_owner.sql` |
| `migrations/02_create_sign_in_grant.{up,down}.sql` | `V202610021201__create_sign_in_grant.sql` / `U202610021201__create_sign_in_grant.sql` |
| `migrations/03_create_sign_in_session.{up,down}.sql` | `V202610021202__create_sign_in_session.sql` / `U202610021202__create_sign_in_session.sql` |
| `migrations/04_create_passkey_tables.{up,down}.sql` | `V202610021203__create_passkey_tables.sql` / `U202610021203__create_passkey_tables.sql` |

| Table | Key columns / constraints | Change |
|---|---|---|
| `owner` | `id` UUID PK · `email` VARCHAR(254) · `canonical_email` VARCHAR(254) UNIQUE (`owner_canonical_email_uq`), CHECK lowercase · `created_at` | added |
| `sign_in_grant` | `link_token_hash` BYTEA UNIQUE, 32 bytes · `code_hash` 32 bytes · `wrong_attempts` SMALLINT DEFAULT 0 CHECK 0–5 · `expires_at > issued_at` · `num_nonnulls(used_at, superseded_at) <= 1` · partial index `sign_in_grant_live_by_canonical_email_idx` WHERE `used_at IS NULL AND superseded_at IS NULL` | added |
| `sign_in_session` | `owner_id` FK → `owner` · `key_hash` BYTEA UNIQUE, 32 bytes · `device_type` IN (`phone`,`tablet`,`computer`,`unknown`) · `last_activity_at >= started_at` · `ended_at` NULL or `>= started_at` · index `sign_in_session_owner_id_idx` | added |
| `user_entities` / `user_credentials` | Spring 7.1.1 shape + `timestamptz` for `created`/`last_used`, FK `user_entity_user_id → user_entities(id) ON DELETE CASCADE`, `user_entities_name_uq`, `user_credentials_user_entity_user_id_idx` | added |

— `data-model.md §Entities + §Indexes, abridged` · full text: [data-model.md](../data-model.md) · SQL: [migrations/](../migrations/)

## API contract

Internal — no API surface.

## Acceptance criteria

### AC-34 — happy

> **Given** a person whose email address has no teleX account
> **When** they enter the address on the sign-in page, open the Sign-in Link from the email and confirm "Continue as <address>"
> **Then** teleX creates their Owner account, signs them in in that browser, offers to create a Passkey, and afterwards shows the empty Inbox with the single step "Connect Telegram"; signing in again with the same address opens the same account, never a second one. Two addresses are the same when they match after ignoring letter case and any "+tag" before the @ (so `Anton+work@Mail.com` and `anton@mail.com` are one account). A sign-in email goes to the address exactly as typed that time; the "New sign-in to teleX" email goes to the address the account was created with
>
> — `spec.md §5, AC-34, verbatim` · full text: [spec.md](../spec.md)

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

- [ ] Copy each staged `.up.sql` verbatim to `backend/app/src/main/resources/db/migration/V<version>__<name>.sql` (versions above, after the baseline `V202609300000`).
- [ ] Copy each staged `.down.sql` to `backend/app/src/main/resources/db/rollback/U<same-version>__<name>.sql`.
- [ ] Run `./gradlew :backend:app:integrationTest --tests 'telex.MigrationRollbackIT'`; it discovers the new pairs on its own — extend it only if it doesn't.
- [ ] Leave the staged files in `docs/features/platform-skeleton/migrations/` as the design record (do not delete).

## Edge cases

| Case | Behaviour |
|---|---|
| Rollback order | `U…203` drops `user_credentials` before `user_entities`; `U…202` (session) runs before `U…200` (owner) — Flyway rollback order is reverse version order |
| Re-applying after rollback | Up → down → up succeeds (staged SQL uses `IF NOT EXISTS` / `IF EXISTS`) |
| A test inserts `wrong_attempts = 6` or a 31-byte hash | Rejected by the CHECK constraints — a DB-level backstop; Kotlin enforces the rules first |

## Definition of Done

- [ ] Staged migrations promoted to live `db/migration` + `db/rollback` with matching versions.
- [ ] `MigrationRollbackIT` applies up → down → up cleanly for all five migrations (baseline + four).
- [ ] `ApplicationSmokeIT` still boots against the migrated schema.
- [ ] every Hard Rule inlined above still holds (no `DEFAULT now()`, no plaintext secret columns).
- [ ] `./gradlew spotlessCheck detekt` clean.
