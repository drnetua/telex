---
id: T1
title: "Promote the four staged model-profiles migrations into the live Flyway tree"
layer: "migration"
deps: []
blocks: ["T3", "T10"]
acs: ["AC-212", "AC-217", "AC-220", "AC-229"]
files_hint: ["docs/features/model-profiles/migrations/01_create_model_catalog.up.sql", "docs/features/model-profiles/migrations/01_create_model_catalog.down.sql", "docs/features/model-profiles/migrations/02_create_model_profile.up.sql", "docs/features/model-profiles/migrations/02_create_model_profile.down.sql", "docs/features/model-profiles/migrations/03_create_default_model_profile.up.sql", "docs/features/model-profiles/migrations/03_create_default_model_profile.down.sql", "docs/features/model-profiles/migrations/04_create_model_call.up.sql", "docs/features/model-profiles/migrations/04_create_model_call.down.sql"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T1 — Promote the four staged model-profiles migrations into the live Flyway tree

## Place in the sequence

- **Blocked by:** — · **Blocks:** T3 — Store the catalog snapshot in Postgres, load it at start and hold it in memory, T10 — Persist custom profiles, their chains and the default profile, always scoped by Owner · **Wave:** 1 — needs no code; starts in parallel with T2 and T16.
- **Lane:** `layer: migration` — serialized by `implement`; no other task touches these files.

## Why (user story)

> **As an** Owner
> **I want** to create, duplicate, edit and delete my own Model Profiles, with a Fallback Chain in each slot
> **So that** my helpers use the models I trust, with backups I chose
>
> — `spec.md §4, US-81, verbatim` · full text: [spec.md](../spec.md)

> **As an** Owner
> **I want** teleX to switch to the next model in the chain when a model is gone or fails, and to show me while a profile's main model is missing
> **So that** a vanished or broken model doesn't stop my helpers and a lasting switch doesn't surprise me
>
> — `spec.md §4, US-82, verbatim` · full text: [spec.md](../spec.md)

This task lays down the tables a profile, its Fallback Chains, the Owner's default and every content-free call record are stored in, plus the catalog snapshot that survives a restart.

## Inlined context

> **Migrations:** `backend/app/src/main/resources/db/migration/V<yyyyMMddHHmm>__<name>.sql`, each with a rollback `db/rollback/U<same-version>__<name>.sql`; `MigrationRollbackIT` applies up → down → up for all of them. Feature migrations are staged by `/sdd:data-model` in `docs/features/<slug>/migrations/` and promoted by `implement`.
>
> — `CLAUDE.md §Layout and code conventions, Migrations, verbatim` · full text: [CLAUDE.md](../../../../CLAUDE.md)

> Two modules own tables, and neither reads the other's. `llm` owns the Model Catalog snapshot (`model_catalog_entry`, `model_catalog_state`). `agents` owns custom profiles, their chains, the default profile and the call records. `web` owns none. The baseline `event_publication` table carries `ModelCatalogRefreshed`, `ModelProfileDeleted` and `ModelCallFinished` and needs nothing new.
>
> - Time: `TIMESTAMP WITH TIME ZONE`, written from the injectable `Clock`. No `DEFAULT now()`.
> - Delete strategy: hard delete for custom profiles (AC-220). Their chains and a default that points to them go with them. Call records are append-only in code and are never deleted in E10.
> - Constraint names: `<table>_<what>_{ck|fk|uq|idx}`.
> - **FK to `owner(id)` on the profile tables only.** `model_call.owner_id` has no FK and no index, because the record is a log that outlives its references.
>
> — `data-model.md §Conventions applied + User decision 4, abridged` · full text: [data-model.md](../data-model.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

Staged pairs to promote, in this order (FKs: `default_model_profile → model_profile`, `model_call_attempt → model_call`). Suggested live versions — keep the order, any later timestamp works:

| Staged pair | Live up / rollback |
|---|---|
| `migrations/01_create_model_catalog.{up,down}.sql` | `V202610031200__create_model_catalog.sql` / `U202610031200__create_model_catalog.sql` |
| `migrations/02_create_model_profile.{up,down}.sql` | `V202610031201__create_model_profile.sql` / `U202610031201__create_model_profile.sql` |
| `migrations/03_create_default_model_profile.{up,down}.sql` | `V202610031202__create_default_model_profile.sql` / `U202610031202__create_default_model_profile.sql` |
| `migrations/04_create_model_call.{up,down}.sql` | `V202610031203__create_model_call.sql` / `U202610031203__create_model_call.sql` |

| Table | Key columns / constraints | Change |
|---|---|---|
| `model_catalog_entry` | `model_id` VARCHAR(200) PK · `takes_text`/`takes_images`/`produces_text`/`produces_images` BOOLEAN · prices NUMERIC(14,6) NULL `≥ 0` · `context_length` NULL `> 0` · `model_catalog_entry_fits_a_slot_ck` | added (`llm`) |
| `model_catalog_state` | `id` SMALLINT PK `CHECK id = 1` · `last_refreshed_at`, `last_failed_at` TIMESTAMPTZ NULL · `model_catalog_state_attempted_ck` | added (`llm`) |
| `model_profile` | `id` UUID PK · `owner_id` FK → `owner(id)` · `name` VARCHAR(40) `model_profile_name_trimmed_ck` · `created_at` · `model_profile_owner_id_id_uq` `(owner_id, id)` · `model_profile_owner_id_lower_name_uq` `(owner_id, lower(name))` | added (`agents`) |
| `model_profile_slot_model` | PK `(model_profile_id, slot, position)` · FK ON DELETE CASCADE · `slot IN ('text','vision','image')` · `position 1–3` · `model_id` no FK · `model_profile_slot_model_once_uq` `(model_profile_id, slot, model_id)` | added (`agents`) |
| `default_model_profile` | `owner_id` PK FK → `owner` · `system_profile_key IN ('fast','balanced','careful')` · composite FK `(owner_id, custom_profile_id) → model_profile(owner_id, id)` ON DELETE CASCADE · `default_model_profile_ref_ck` exactly one ref · `chosen_at` | added (`agents`) |
| `model_call` | `id` UUID PK · `owner_id`, `custom_profile_id` **no FK** · `slot` · `outcome IN ('answered','no-model-available','no-model-answered','ai-not-configured')` · `answered_by_model_id` set iff answered · `fallback` · `started_at ≤ finished_at` · `model_call_profile_ref_ck` | added (`agents`) |
| `model_call_attempt` | PK `(model_call_id, position)` · FK ON DELETE CASCADE · `position 1–3` · `outcome IN ('answered','missing','unavailable','provider-error','rate-limited','timeout','too-large','content-refused','invalid-request')` | added (`agents`) |

— `data-model.md §Entities + §Indexes, abridged` · full text: [data-model.md](../data-model.md) · SQL: [migrations/](../migrations/)

## API contract

Internal — no API surface.

## Acceptance criteria

### AC-212 — error (US-80)

> **Given** the Model Catalog loaded earlier, and its latest automatic refresh has failed
> **When** the Owner opens the catalog
> **Then** the Owner sees the last known list with a note that it couldn't be updated and the time it is from, and profiles keep working with that list, also after teleX restarts; on a first start with no list ever loaded, the Owner sees "The model list isn't available yet", every slot shows no model available, and teleX retries every 5 minutes
>
> — `spec.md §5, AC-212, verbatim` · full text: [spec.md](../spec.md)

### AC-217 — domain invariant (US-81)

> **Given** an Owner editing a slot that already holds three models, or a slot that already contains a given model
> **When** the Owner tries to add a fourth model, or the same model a second time
> **Then** the model isn't added and the Owner is told that a slot holds at most three models, each only once
>
> — `spec.md §5, AC-217, verbatim` · full text: [spec.md](../spec.md)

### AC-220 — happy (US-81)

> **Given** an Owner whose default profile is their custom profile "Cheap vision"
> **When** the Owner deletes "Cheap vision" and confirms
> **Then** the profile disappears from their list, the default goes back to Balanced, and the Owner is told that Balanced is now the default
>
> — `spec.md §5, AC-220, verbatim` · full text: [spec.md](../spec.md)

### AC-229 — cross-context (US-82)

> **Given** an Owner whose AI work makes calls through a profile, some answered by the main model, some after a fallback and some failing
> **When** the calls finish
> **Then** each call is recorded without any message content: when it happened, the Owner, profile, slot, every model tried with its outcome, the model that answered (if any) and whether a fallback happened (skipping a main model that is missing from the catalog counts as one), so that run details and the fallback KPI read from one place
>
> — `spec.md §5, AC-229, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Copy each staged `.up.sql` to `backend/app/src/main/resources/db/migration/V2026100312NN__<name>.sql` and each `.down.sql` to `db/rollback/U<same>__<name>.sql`, unchanged
- [ ] Run `./gradlew :backend:app:integrationTest --tests 'telex.MigrationRollbackIT'` — up → down → up green for all ten migrations
- [ ] Add `backend/app/src/integrationTest/kotlin/telex/ModelProfilesSchemaIT.kt` (precedent `IdentitySchemaIT.kt`): a 4th chain row, a duplicate model in one slot and a default with both refs set are rejected; deleting a profile removes its chain rows and its default row but keeps `model_call` rows

## Edge cases

| Case | Behaviour |
|---|---|
| Reorder inside a chain (positions 1 ↔ 2) | Done by delete-then-insert in one transaction (T10), so the PK never collides mid-way |
| Profile deleted while call records point at it | `model_call.custom_profile_id` has no FK — the records stay |
| A model longer than 200 characters | Not stored; the adapter skips and logs it (T2) |
| Rollback of `02` while `03` exists | Rollbacks run in reverse order (`U…1203` first) — `MigrationRollbackIT` proves it |

## Definition of Done

- [ ] the staged pairs are promoted to live `db/migration` + `db/rollback`, then apply and revert cleanly in `MigrationRollbackIT`
- [ ] `ModelProfilesSchemaIT` passes on Testcontainers pgvector
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
