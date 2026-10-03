---
id: T1
title: "Promote the staged owner-preferences migration into the live Flyway tree"
layer: "migration"
deps: []
blocks: ["T3"]
acs: ["AC-179", "AC-183", "AC-186"]
files_hint: ["docs/features/app-shell/migrations/01_add_owner_preferences.up.sql", "docs/features/app-shell/migrations/01_add_owner_preferences.down.sql"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T1 — Promote the staged owner-preferences migration into the live Flyway tree

## Place in the sequence

- **Blocked by:** — · **Blocks:** T3 — Read and change the Owner's theme and timezone in `identity` · **Wave:** 1 — the schema has no upstream dependency.
- **Lane:** own lane (`layer: migration` is always serialized by `implement`).

## Why (user story)

> **As an** Owner
> **I want** to choose a light, dark or system theme once and have it on all my devices
> **So that** teleX is comfortable to read at any time of day
>
> — `spec.md §4, US-73, verbatim` · full text: [spec.md](../spec.md)

> **As an** Owner
> **I want** teleX to know my timezone and show dates and times in it
> **So that** times on every screen match my day, wherever my browser runs
>
> — `spec.md §4, US-74, verbatim` · full text: [spec.md](../spec.md)

This task gives the account the three columns that make theme and timezone follow the Owner to every device.

## Inlined context

> **Theme and timezone are typed columns on the Owner.** `identity` owns them, `me` returns them, one preferences call changes them, and other modules read the timezone through `identity`'s public API.
>
> — `sad.md §4, strategic choice 4, verbatim` · decision: [adr/0005](../adr/0005-store-theme-and-timezone-as-columns-on-the-owner-row.md)

> Conventions: light named `CHECK`s on enum-like values (`<table>_<column>_ck`) · no generic `updated_at` · `IF NOT EXISTS` / `IF EXISTS` so a partly applied file re-runs cleanly · constant `DEFAULT`s only where they make a `NOT NULL` addition safe (metadata-only on Postgres 17, no backfill).
> *Existing Owners' timezone:* not backfilled from `sign_in_session.time_zone` (user decision, 2026-10-03). It stays `NULL` until AC-183's first open saves it from the device.
>
> — `data-model.md §Conventions applied + §Not stored, abridged` · full text: [data-model.md](../data-model.md)

> **Migrations:** `backend/app/src/main/resources/db/migration/V<yyyyMMddHHmm>__<name>.sql`, each with a rollback `db/rollback/U<same-version>__<name>.sql`; `MigrationRollbackIT` applies up → down → up for all of them.
>
> — `CLAUDE.md §Layout and code conventions, Migrations, abridged`

**Fallback:** insufficient or contradicted by the code → read the named file in full
([data-model.md](../data-model.md) · [adr/0005](../adr/0005-store-theme-and-timezone-as-columns-on-the-owner-row.md)) and follow it. Do not guess.

## Data delta

Staged pair: `docs/features/app-shell/migrations/01_add_owner_preferences.up.sql` / `.down.sql` → promote to `db/migration/V<yyyyMMddHHmm>__add_owner_preferences.sql` + `db/rollback/U<same>__add_owner_preferences.sql` (version later than `V202610021600`).

| Column | Type | Constraints | Change |
|---|---|---|---|
| `owner.theme` | `VARCHAR(6)` | NOT NULL DEFAULT `'system'`, CHECK `owner_theme_ck` in (`light`, `dark`, `system`) | added |
| `owner.time_zone` | `VARCHAR(64)` | NULL — `NULL` means "not saved yet" | added |
| `owner.time_zone_is_fallback` | `BOOLEAN` | NOT NULL DEFAULT `false`, CHECK `owner_time_zone_fallback_ck`: `NOT time_zone_is_fallback OR time_zone = 'UTC'` | added |

No new index — every query filters on `owner.id` (`owner_pkey`).

— `data-model.md §Entities, owner (changed) + §Indexes, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no API surface.

## Acceptance criteria

### AC-179 — happy

> **Given** a signed-in Owner on Profile and security with the light theme
> **When** they choose Dark
> **Then** every screen of teleX switches to the dark theme at once, without a reload, and the choice is saved to their account
>
> — `spec.md §5, AC-179, verbatim` · full text: [spec.md](../spec.md)

### AC-183 — happy

> **Given** an Owner who has no timezone saved yet
> **When** they open teleX after this change
> **Then** their timezone is taken from the device and saved to their account (UTC, with a hint on Profile and security to pick their own, when the device's timezone can't be read or isn't on the list), Profile and security shows it, a device in another timezone later doesn't change it, and the dates on that page (when a Sign-in Session or Passkey was last used) are shown in it
>
> — `spec.md §5, AC-183, verbatim` · full text: [spec.md](../spec.md)

### AC-186 — domain invariant

> **Given** an Owner with a saved timezone
> **When** they try to leave the timezone empty
> **Then** teleX doesn't allow it and keeps the current one ("an Owner always has exactly one timezone"); the only way to change it is to pick another from the list
>
> — `spec.md §5, AC-186, verbatim` · full text: [spec.md](../spec.md)

This task delivers only the storage half of these ACs (the columns and their checks); the rules live in T3.

## Checklist

- [ ] Copy `01_add_owner_preferences.up.sql` to `backend/app/src/main/resources/db/migration/V<yyyyMMddHHmm>__add_owner_preferences.sql`
- [ ] Copy `01_add_owner_preferences.down.sql` to `backend/app/src/main/resources/db/rollback/U<same>__add_owner_preferences.sql`
- [ ] Run `./gradlew :backend:app:integrationTest --tests 'telex.MigrationRollbackIT'`
- [ ] Add (or extend) an integration assertion that `owner_theme_ck` rejects `'blue'` and `owner_time_zone_fallback_ck` rejects `time_zone_is_fallback = true` with `time_zone = 'Europe/Kyiv'`

## Edge cases

| Case | Behaviour |
|---|---|
| Existing Owner rows at migration time | Get `theme = 'system'`, `time_zone = NULL`, `time_zone_is_fallback = false`; no backfill |
| Migration re-run after a partial apply | `IF NOT EXISTS` / `DROP CONSTRAINT IF EXISTS` make it idempotent |
| Rollback | Drops the three columns, and their constraints with them |

## Definition of Done

- [ ] Staged pair promoted to live `db/migration` + `db/rollback`; `MigrationRollbackIT` applies up → down → up green
- [ ] Both `CHECK`s proven by an integration assertion
- [ ] `./gradlew spotlessCheck detekt` clean
