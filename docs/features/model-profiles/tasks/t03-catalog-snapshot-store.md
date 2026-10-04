---
id: T3
title: "Store the catalog snapshot in Postgres, load it at start and hold it in memory"
layer: "infra"
deps: ["T1", "T2"]
blocks: ["T4", "T11"]
acs: ["AC-212"]
files_hint: ["backend/app/src/main/kotlin/telex/llm/ModelCatalog.kt", "backend/app/src/main/kotlin/telex/llm/internal/catalog/", "backend/app/src/integrationTest/kotlin/telex/llm/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T3 — Store the catalog snapshot in Postgres, load it at start and hold it in memory

## Place in the sequence

- **Blocked by:** T1 — Promote the four staged model-profiles migrations into the live Flyway tree, T2 — Define the llm catalog value types, the slot-fit rule and the OpenRouter model-list parser · **Blocks:** T4 — Refresh the catalog from OpenRouter at start, every 24 h and every 5 min after a failure, T11 — Serve the catalog view, the profile list with picker data, one profile and a new-profile draft · **Wave:** 2 — needs the tables (T1) and the value types (T2).
- **Lane:** shares `telex/llm/internal/catalog/` with T4 — serialized (T4 depends on this task).

## Why (user story)

> **As an** Owner
> **I want** to see the models teleX can use, with what each one can do and what it costs
> **So that** I can tell which models suit text, photos or images before I build a profile
>
> — `spec.md §4, US-80, verbatim` · full text: [spec.md](../spec.md)

This task keeps the last known Model Catalog across restarts and outages, so profiles keep working from it while the provider is unreachable.

## Inlined context

> **Aggregate root:** the snapshot as a whole (`ModelCatalog`). A refresh replaces every row and updates `model_catalog_state` in **one transaction**, so a restart never loads a half-written list (AC-212).
> **Access patterns:** load all rows at startup into the in-memory holder (sad §7), then replace all rows on each successful refresh. Both are full-table operations on about 500 rows, so the PK is the only index.
>
> — `data-model.md §Entities, model_catalog_entry, abridged` · full text: [data-model.md](../data-model.md)

> | `id` | SMALLINT | PK, `CHECK id = 1` | Single row, upserted (`INSERT … ON CONFLICT (id) DO UPDATE`) |
> | `last_refreshed_at` | TIMESTAMPTZ | NULL | Last successful refresh: "the time it is from" (AC-212), the catalog age KPI |
> | `last_failed_at` | TIMESTAMPTZ | NULL | Last failed refresh. `last_failed_at > last_refreshed_at` (or no success yet) → "couldn't be updated" note (AC-212) |
>
> **States:** no row = never attempted. A row with `last_refreshed_at IS NULL` = never loaded ("The model list isn't available yet", AC-212). A missing provider key writes nothing: "not configured" is a runtime state from settings (AC-226).
>
> — `data-model.md §Entities, model_catalog_state, abridged` · full text: [data-model.md](../data-model.md)

> The catalog is about 300–500 slot-fit models (spec §6 sizes the page for 500). It is held in memory after start (well under 1 MB) and served from memory, so the Models page doesn't touch OpenRouter.
>
> — `sad.md §7, Scaling thresholds, verbatim` · full text: [sad.md](../sad.md)

> | Time | The injected `java.time.Clock` bean for refresh times, catalog age and call timestamps, so freshness tests can control the clock (spec §6) | `ClockConfiguration.kt` |
>
> — `sad.md §8, Time, verbatim` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Table | Columns this task reads / writes | Change |
|---|---|---|
| `model_catalog_entry` | all columns — delete-all + batch insert on replace; full select at start | read / write |
| `model_catalog_state` | `last_refreshed_at` on a successful replace, `last_failed_at` on a failure (upsert on `id = 1`) | read / write |

— `data-model.md §Entities, llm — Model Catalog snapshot, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no HTTP surface. `llm` root API `ModelCatalog` (sad §5: "current snapshot (models, capabilities, prices, refreshedAt, state)"). `state` follows the contract:

> `not-configured`: no provider key (AC-226). `not-loaded`: no successful refresh ever (model_catalog_state.last_refreshed_at NULL, AC-212). `update-failed`: last_failed_at > last_refreshed_at (AC-212). `current` otherwise.
>
> — `contracts/openapi.yaml, schema ModelCatalog.state, verbatim` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-212 — error (US-80)

> **Given** the Model Catalog loaded earlier, and its latest automatic refresh has failed
> **When** the Owner opens the catalog
> **Then** the Owner sees the last known list with a note that it couldn't be updated and the time it is from, and profiles keep working with that list, also after teleX restarts; on a first start with no list ever loaded, the Owner sees "The model list isn't available yet", every slot shows no model available, and teleX retries every 5 minutes
>
> — `spec.md §5, AC-212, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `CatalogSnapshotStore` (JdbcClient): `replace(models, refreshedAt)` in one transaction, `recordFailure(at)`, `load()` — `backend/app/src/main/kotlin/telex/llm/internal/catalog/`
- [ ] `CatalogHolder`: an atomic in-memory snapshot loaded on `ApplicationReadyEvent` (or at bean init) from the store — `backend/app/src/main/kotlin/telex/llm/internal/catalog/`
- [ ] `ModelCatalog` API at the `llm` root: `snapshot()` (models by id, refreshedAt, failedAt, state), `find(modelId)` — `backend/app/src/main/kotlin/telex/llm/ModelCatalog.kt`
- [ ] Integration test with a fixed `Clock`: replace → restart context → same snapshot; failure after success → `update-failed`; failure only → `not-loaded` — `backend/app/src/integrationTest/kotlin/telex/llm/`

## Edge cases

| Case | Behaviour |
|---|---|
| Replace fails half-way | Transaction rolls back; the previous snapshot stays in DB and memory |
| First start, no row in `model_catalog_state` | Empty snapshot, state `not-loaded` |
| A refresh that fails after an earlier success | Models stay, `last_failed_at` set, state `update-failed` |
| Concurrent readers during a replace | Readers see the old or the new snapshot, never a mix (swap one reference) |

## Definition of Done

- [ ] the restart integration test passes on Testcontainers
- [ ] `ModularityTest` green; `internal/catalog` is not reachable from other modules
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
