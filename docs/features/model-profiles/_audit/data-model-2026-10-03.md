# Audit — data-model model-profiles (2026-10-03)

**Migrations are staged. They are not in the live `db/migration` / `db/rollback` tree yet; `implement` promotes them.**

## Staged migrations

| Ordinal | Up | Down | Module | Creates |
|---|---|---|---|---|
| 01 | `docs/features/model-profiles/migrations/01_create_model_catalog.up.sql` | `01_create_model_catalog.down.sql` | `llm` | `model_catalog_entry`, `model_catalog_state` |
| 02 | `migrations/02_create_model_profile.up.sql` | `02_create_model_profile.down.sql` | `agents` | `model_profile` (FK → `owner`) + `model_profile_owner_id_lower_name_uq`, `model_profile_slot_model` |
| 03 | `migrations/03_create_default_model_profile.up.sql` | `03_create_default_model_profile.down.sql` | `agents` | `default_model_profile` (FK → `owner`, composite FK → `model_profile`) |
| 04 | `migrations/04_create_model_call.up.sql` | `04_create_model_call.down.sql` | `agents` | `model_call`, `model_call_attempt` |

Order matters: 03 depends on 02 (composite FK). 02 and 03 depend on E01's `owner` (`V202610021200`).

**Promote-time hint:** Flyway, `V<yyyyMMddHHmm>__<snake_name>.sql` in `backend/app/src/main/resources/db/migration/` paired with `U<same-version>__<snake_name>.sql` in `db/rollback/`. The latest live migration is `V202610021600__widen_user_entity_display_name.sql`. `implement` assigns timestamps after the latest one present at promotion, in ordinal order (another in-flight feature such as telegram-link may promote first), and changes each `-- Reverts …` header line to the real `V…` file name. `MigrationRollbackIT` then covers up → down → up.

## Verification done at this stage

On a disposable `pgvector/pgvector:pg17` container, after applying the 6 live migrations:
- all 4 ups applied, re-applied (idempotent, `IF NOT EXISTS`), all 4 downs in reverse order returned to the 6 pre-existing tables, ups re-applied cleanly (13 tables);
- 13 negative inserts each rejected by the intended constraint: case-insensitive name clash, untrimmed and empty name, duplicate model in a slot, position 4, unknown slot, another Owner's profile as default (composite FK), both reference columns set, unknown system key, a model fitting no slot, a second state row, `answered` without a model, no profile reference;
- deleting a custom profile that was the default removed its default row and chain rows (cascade) and kept the call record and its attempts;
- `EXPLAIN` (seqscan off) of find by `(owner_id, id)`, count by `owner_id`, name lookup by `(owner_id, lower(name))` and the chain load use the documented indexes.

## Decisions taken in this stage (user-confirmed 2026-10-03)

1. **`ProfileRef` storage = two columns** (`system_profile_key`, `custom_profile_id`, exactly one set), with a composite FK `(owner_id, custom_profile_id) → model_profile(owner_id, id)` where tenancy matters. This resolves feature ADR-0005's "data-model picks which" and is a contract E09 copies. Consider adding a line to ADR-0005's consequences during `tasks`.
2. **Chains and attempts in child tables**, not arrays.
3. **Catalog snapshot as one row per model** plus a single-row state table, not JSONB.
4. **FK to `owner(id)` on `model_profile` and `default_model_profile` only.** `model_call` keeps `owner_id` without FK or index.

## Convention deviations / notes

- **Cross-module FK** `agents` tables → `identity`'s `owner(id)`. The architecture map says "no cross-module table access or joins". An FK is neither (no code reads `owner`), but it couples migration order to E01. User-confirmed (decision 4).
- **Natural keys** instead of UUIDv7 on `model_catalog_entry` (provider model id), `default_model_profile` (`owner_id`), and the two child tables (composite PKs). Precedent: E01 passkey tables keyed by WebAuthn ids.
- **First expression index** in the repo (`lower(name)`), from sad §8 "Concurrency".
- **20-profile limit** relies on a transaction-scoped advisory lock per Owner in the save path (no schema object). `implement` must include it, and an integration test with two parallel creates at 19 should prove it (AC-218).
- **No secondary index on `model_call`.** No E10 query reads it, and the KPI and run-details indexes arrive with E14 or the retention job (sad §7 threshold). Not a self-check failure: FK indexes are covered by PKs.

## Drift

No drift possible yet: `telex/llm` and `telex/agents` contain only `package-info.java`. No `_drift/` files.

## Self-check (4 mandatory)

| Check | Result |
|---|---|
| Naming matches the repo | Pass: snake_case singular tables, `<table>_<what>_{ck,fk,uq,idx}`, `TIMESTAMP WITH TIME ZONE`, `VARCHAR(N)`, `IF NOT EXISTS`, `-- Reverts …` header in downs |
| Down reversibility | Pass: every CREATE TABLE has a DROP TABLE (child first). Indexes and constraints drop with their tables. Verified up → down → up |
| FK indexes | Pass: `model_profile.owner_id` → leading column of `model_profile_owner_id_id_uq`. `model_profile_slot_model.model_profile_id` → PK. `default_model_profile` both FKs → PK `owner_id`. `model_call_attempt.model_call_id` → PK |
| Convention adherence | Pass, with the deviations listed above (all user-confirmed or with precedent) |

## TBD

None in `data-model.md`.

## Next stage

`/sdd:api model-profiles`: the feature adds a REST API (`/api/models/**`) and the `ProfileCalls` port, so the contract stage applies.
