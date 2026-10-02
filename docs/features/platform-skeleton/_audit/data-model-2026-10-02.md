# Audit — data-model platform-skeleton (2026-10-02)

**Migrations are staged. They are not in the live `db/migration` / `db/rollback` tree yet; `implement` promotes them.**

## Staged migrations

| Ordinal | Up | Down | Creates |
|---|---|---|---|
| 01 | `docs/features/platform-skeleton/migrations/01_create_owner.up.sql` | `01_create_owner.down.sql` | `owner` + `owner_canonical_email_uq` |
| 02 | `migrations/02_create_sign_in_grant.up.sql` | `02_create_sign_in_grant.down.sql` | `sign_in_grant` + 2 indexes |
| 03 | `migrations/03_create_sign_in_session.up.sql` | `03_create_sign_in_session.down.sql` | `sign_in_session` (FK → `owner`) + 2 indexes |
| 04 | `migrations/04_create_passkey_tables.up.sql` | `04_create_passkey_tables.down.sql` | `user_entities`, `user_credentials` (Spring WebAuthn) + 2 indexes |

**Promote-time hint:** Flyway, `V<yyyyMMddHHmm>__<snake_name>.sql` in `backend/app/src/main/resources/db/migration/` paired with `U<same-version>__<snake_name>.sql` in `db/rollback/`. The only live migration is `V202609300000__baseline.sql`. `implement` assigns timestamps after it, in ordinal order (e.g. `V202610…__create_owner.sql`), and changes the `-- Reverts …` header line to the real `V…` file name. `MigrationRollbackIT` then covers up → down → up.

## Verification done at this stage

On a disposable `postgres:17-alpine` container:
- all 4 ups applied, re-applied (idempotent, `IF NOT EXISTS`), all 4 downs in reverse order left 0 tables, ups re-applied cleanly;
- the ADR-0003 redeem `UPDATE … RETURNING` succeeded once on a fresh grant;
- `EXPLAIN` of the supersede lookup uses `sign_in_grant_live_by_canonical_email_idx`;
- `sign_in_session_device_type_ck` rejected an invalid `device_type`.

## Decisions taken in this stage (user-confirmed)

1. WebAuthn challenge lives in a short-lived `HttpSession` for the ceremony only — no table. Resolves sad §6 "Flagged by sequences" item 1. `web` security config must allow `HttpSession` creation for `/webauthn/**` and `/login/webauthn` only; consider recording this in ADR-0001's consequences during `tasks`/`implement`.
2. Light `CHECK` constraints are used (first in the repo — establishes the convention).
3. `user_credentials.created` / `last_used` are `timestamptz` instead of Spring's `timestamp`.

## Convention deviations

- **Spring passkey DDL** (ADR-0002 says "to Spring's schema"): `timestamptz`, FK credential → user entity, two added indexes. Column names and the remaining types are unchanged. Risk: Spring's JDBC mapping of `timestamptz` — cover with an integration save/reload test.
- **Passkey tables keyed by text WebAuthn ids**, not UUIDv7 — the documented ADR-0002 exception.
- **No FK `user_entities.name` → `owner.id`** (text vs UUID). Ownership is enforced in `identity` code; a stray user entity can't reach another Owner's data because every query filters by the caller's own `OwnerId`.
- **No `DEFAULT now()`** on timestamp columns, so the injectable `Clock` stays the single time source (sad §8). `wrong_attempts DEFAULT 0` is the only default.
- **`IF NOT EXISTS`** in DDL, which the baseline doesn't use. It's harmless under Flyway and follows the skill's idempotency rule.

## Self-check (4 mandatory)

| Check | Result |
|---|---|
| Naming matches the repo | ✅ snake_case singular tables (`event_publication` precedent); Spring tables keep Spring's names; index suffixes `_idx` as in the baseline, `_uq` for unique |
| Down reversibility | ✅ every `CREATE TABLE` has a `DROP TABLE` (indexes and constraints drop with their tables); 04 drops `user_credentials` before `user_entities`; verified on Postgres |
| FK indexes | ✅ `sign_in_session.owner_id` → `sign_in_session_owner_id_idx`; `user_credentials.user_entity_user_id` → `user_credentials_user_entity_user_id_idx` |
| Convention adherence | ✅ UUIDv7 PKs, `timestamptz`, paired rollback; deviations listed above |

## Drift detection

No drift possible: `telex.identity` holds only `package-info.java` and no domain classes exist yet. The domain layer is written in `implement` against this model.

## Breaking-change decompositions

None. Every table is new and no existing table changes.

## Open / TBD

- No `<!-- TBD -->` markers.
- `identity`'s `package-info.java` must gain `mail` in `allowedDependencies` (sad §5) — a code task, not a schema change.

Next stage: `/sdd:api platform-skeleton`.
