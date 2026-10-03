# Audit — data-model app-shell (2026-10-03)

**Migrations are staged. They are not in the live `db/migration` / `db/rollback` tree yet; `implement` promotes them.**

## Staged migrations

| Ordinal | Up | Down | Changes |
|---|---|---|---|
| 01 | `docs/features/app-shell/migrations/01_add_owner_preferences.up.sql` | `01_add_owner_preferences.down.sql` | `owner` + `theme`, `time_zone`, `time_zone_is_fallback`, CHECKs `owner_theme_ck`, `owner_time_zone_fallback_ck` |

One file for three columns: they form one cohesive change (ADR-0005), the fallback CHECK spans two of them, and the down drops all three together. The anti-pattern is unrelated ALTERs bundled together, and this isn't that.

**Promote-time hint:** Flyway, `V<yyyyMMddHHmm>__add_owner_preferences.sql` in `backend/app/src/main/resources/db/migration/` paired with `U<same-version>__add_owner_preferences.sql` in `db/rollback/`. The latest live migration is `V202610021600__widen_user_entity_display_name.sql`. `implement` assigns a later timestamp at promotion, since telegram-link or model-profiles may promote first in wave 3. It also changes the down file's `-- Reverts …` header to the real `V…` name. `MigrationRollbackIT` then covers up → down → up.

## Verification done at this stage

On a disposable `pgvector/pgvector:pg17` container, after applying all six live migrations:
- the up applied, then re-applied cleanly (idempotent: `ADD COLUMN IF NOT EXISTS`, drop-then-add for the table CHECK);
- an existing `owner` row got `theme = 'system'`, `time_zone = NULL`, `time_zone_is_fallback = false`;
- the first-save-only `UPDATE … WHERE id = ? AND time_zone IS NULL` updated 1 row, and a second one (UTC fallback) updated 0 rows, so the first save won;
- `owner_theme_ck` rejected `theme = 'blue'`, and `owner_time_zone_fallback_ck` rejected the fallback flag on `Europe/Kyiv`;
- `EXPLAIN` of the conditional update uses `owner_pkey`;
- the down removed all three columns, and the up re-applied after it.

## Decisions taken in this stage (user-confirmed)

1. No backfill of `owner.time_zone` from `sign_in_session.time_zone`. Existing Owners get their zone from AC-183's first open.
2. Add `owner_time_zone_fallback_ck` (fallback implies UTC) on top of the theme CHECK, following the light-CHECK convention from platform-skeleton.

## Convention deviations

- None from the repo. Constant `DEFAULT`s on the two `NOT NULL` columns are the only defaults besides `sign_in_grant.wrong_attempts`. They make the addition to an existing table safe (metadata-only on PG 17) and match ADR-0005's option 1 verbatim.

## Breaking-change decompositions

- None needed. The new `NOT NULL` columns carry constant defaults, so existing rows are valid at once and running E01 code (which names its columns explicitly) is unaffected. No rename or drop.

## Drift

`identity.internal.owner.Owners` reads and writes `id`, `email`, `canonical_email` and `created_at`, which match the live `owner` columns. The three new columns have no Kotlin field yet. That's expected before `implement` (they arrive with `OwnerPreferences` and the `Me` change), so this isn't drift. No `_drift/` fixes.

## Self-check (4 mandatory)

| Check | Result |
|---|---|
| Naming matches the repo | Pass: snake_case columns, `owner_<column>_ck` constraints, `NN_<verb>_<entity>` staging names |
| Down reversibility | Pass: 3 × ADD COLUMN ↔ 3 × DROP COLUMN, and the constraints go with their columns. Verified up → down → up |
| FK indexes | Pass: no new FK |
| Convention adherence | Pass: light CHECKs, no `updated_at`, `IF NOT EXISTS`, no `DEFAULT now()` |

## TBD

- None in `data-model.md`.

Next stage: `/sdd:api app-shell`.
