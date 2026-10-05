# Audit — data-model telegram-link (2026-10-03)

**Migrations are staged. They are not in the live `db/migration` / `db/rollback` tree yet; `implement` promotes them.**

## Staged migrations

| Ordinal | Up | Down | Creates |
|---|---|---|---|
| 01 | `docs/features/telegram-link/migrations/01_create_owner_key.up.sql` | `01_create_owner_key.down.sql` | `owner_key` (`identity`, FK → `owner`) |
| 02 | `migrations/02_create_linked_account.up.sql` | `02_create_linked_account.down.sql` | `linked_account` (`messaging`, FK → `owner`) + 3 indexes + `UNIQUE (id, owner_id)` |
| 03 | `migrations/03_create_channel.up.sql` | `03_create_channel.down.sql` | `channel` (`messaging`, composite FK → `linked_account`, ON DELETE CASCADE) + 1 index |

**Promote-time hint:** Flyway, `V<yyyyMMddHHmm>__<snake_name>.sql` in `backend/app/src/main/resources/db/migration/` paired with `U<same-version>__<snake_name>.sql` in `db/rollback/`. The latest live migration is `V202610021600__widen_user_entity_display_name.sql`. `implement` assigns timestamps after it in ordinal order (01 → 02 → 03; 03 depends on 02, and 01/02 on E01's `owner`), at promotion time, since E06 `app-shell` or E10 `model-profiles` (same wave) may promote first. It also changes the `-- Reverts …` header line to the real `V…` file name. `MigrationRollbackIT` then covers up → down → up.

## Verification done at this stage

On a disposable `pgvector/pgvector:pg17` container, after applying all six live migrations:
- the 3 ups applied, re-applied (idempotent, `IF NOT EXISTS`), the 3 downs in reverse order left 0 of the new tables, the ups re-applied cleanly;
- `linked_account_telegram_user_id_uq` rejected a second Owner linking the same Telegram user (AC-04);
- `linked_account_session_required_ck` rejected a `connected` row without a Telegram session; a `session_lost` row with neither session nor key (master-key reset) was accepted; `linked_account_session_pair_ck` rejected a session id without a sealed key;
- `channel_linked_account_fk` rejected a chat whose `owner_id` differs from its account's Owner (AC-03);
- `INSERT … ON CONFLICT (linked_account_id, telegram_chat_id) DO UPDATE` updated the existing chat in place;
- deleting the account cascaded its chats to 0 rows (AC-111);
- `EXPLAIN`: lookup by `telegram_session_id` uses `linked_account_telegram_session_id_uq`; the list-my-accounts query uses `linked_account_owner_id_idx` with an index-only count over `channel_linked_account_telegram_chat_uq`.

ER diagram render-checked with `@mermaid-js/mermaid-cli` 11.

## Decisions taken in this stage (user-confirmed)

1. **No separate master-key check value.** Startup opens any one `owner_key` row; zero rows = installation that never stored a key. Same behaviour as sad §7 / ADR-0003, one table fewer. The wording "identity keeps a key-check value … records a new key-check value" in sad §7, §11 and ADR-0003 Consequences should be read as "the stored Owner keys are the check" — patch when `implement` touches those docs, or via `/sdd:design` reconcile.
2. **Synced chat count is derived** (`COUNT(channel)`), not stored. `linked_account` stores only `chats_total` and `chat_sync_completed_at`. Deviates from sad §6 flow 11 ("store the synced count") and the §6 persist hint "Sync counts (synced, total, finished)".

## Decisions taken in this stage (inferred, not asked)

- Aggregates from ADR-0002 / ADR-0003: `linked_account` root with `channel`; `owner_key` its own root in `identity`.
- Masked phone stored structured (`phone_country_code` + `phone_last_digits`), formatted in the UI, so the "never the full number" rule is checkable by `CHECK`.
- `channel` carries `owner_id` (sad §8 convention) and a composite FK `(linked_account_id, owner_id)` → `linked_account(id, owner_id)`, so the database itself keeps a chat on its account's Owner. Adds `linked_account_id_owner_uq`.
- `telegram_session_id` / `tdlib_key_sealed` nullable only for the master-key reset path (sad §7); a normal Session lost keeps both, so "Sign in again" can destroy the old directory.
- The TDLib state sequence (flow 9) is in memory only: each new client restarts it.
- `folder_ids` as `INTEGER[]` (first array column in the repo) rather than a join table; E02 only stores it, E04 may normalise if it queries by folder.

## Convention deviations

- **First `INTEGER[]` column.** Spring `JdbcClient` binds it via `Connection.createArrayOf("integer", …)`; cover with the upsert integration test.
- **First composite FK and `ON DELETE CASCADE` on a teleX table** (E01 used CASCADE only on Spring's passkey table). The unlink still deletes explicitly in code; the cascade makes "one transaction deletes the chat list" hold even if a code path forgets.
- **`owner_key` keyed by `owner_id`**, not a UUIDv7 surrogate: a strict 1:1 with the Owner.
- **No `DEFAULT`s at all**, including `folder_ids` — the code writes `{}` explicitly.

## Self-check (4 mandatory)

| Check | Result |
|---|---|
| Naming matches the repo | ✅ snake_case singular tables; `_idx`, `_uq`, `_fk`, `_ck` suffixes as in E01; `<table>_<columns>_<suffix>` index names |
| Down reversibility | ✅ every `CREATE TABLE` has a `DROP TABLE` (indexes and constraints drop with it); downs run 03 → 02 → 01; verified on Postgres |
| FK indexes | ✅ `owner_key.owner_id` → PK; `linked_account.owner_id` → `linked_account_owner_id_idx`; `channel(linked_account_id, owner_id)` → `channel_linked_account_telegram_chat_uq` (leading `linked_account_id`, used by the cascade per `EXPLAIN`) |
| Convention adherence | ✅ UUIDv7 PKs, `timestamptz` without defaults, light `CHECK`s, paired rollback; deviations listed above |

## Drift detection

No drift possible: `telex.messaging` and `telex.telegram` hold only `package-info.java`, and `identity` has no key-related classes yet. The domain layer is written in `implement` against this model.

## Breaking-change decompositions

None. All three tables are new; no existing table changes.

## Open / TBD

- No `<!-- TBD -->` markers.
- sad §6 flagged for `/sdd:api`: the answer to "Sign in again" on a Connected account — no schema impact.
- sad §6 flagged for the spike: if TDLib doesn't report a closed authorization on an idle account within 5 min, a periodic liveness check is needed — in memory, no schema impact expected.
- Doc wording follow-ups from decisions 1 and 2 above (sad §6 flow 11 + persist hints, §7, §11; ADR-0003 Consequences).

Next stage: `/sdd:api telegram-link`.
