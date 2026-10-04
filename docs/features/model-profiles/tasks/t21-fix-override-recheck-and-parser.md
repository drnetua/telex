---
id: T21
title: "Re-check the Operator overrides against the new catalog and skip bad provider entries"
layer: "app"
deps: []
acs: ["AC-227", "AC-212"]
files_hint: ["backend/app/src/main/kotlin/telex/llm/internal/catalog/CatalogRefresher.kt", "backend/app/src/main/kotlin/telex/llm/internal/catalog/CatalogHolder.kt", "backend/app/src/main/kotlin/telex/llm/internal/catalog/CatalogSnapshotStore.kt", "backend/app/src/main/kotlin/telex/llm/internal/openrouter/ModelListParser.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/OverrideValidator.kt", "backend/app/src/test/kotlin/telex/llm/ModelListParserTest.kt", "backend/app/src/test/kotlin/telex/agents/internal/profile/OverrideValidatorTest.kt", "backend/app/src/integrationTest/kotlin/telex/"]
owner: "Anton Husiev"
estimate: "S"
origin: "review-2026-10-03"
status: "todo"
---

# T21 — Re-check the Operator overrides against the new catalog and skip bad provider entries

## Origin

Follow-up task from the independent review — [review-2026-10-03.md](../_review/review-2026-10-03.md). Each finding below cites `file:line` as of commit `9e411fd`; re-check the lines before editing. The ACs are in [spec.md](../spec.md) §5 (source of truth); contracts in [data-model.md](../data-model.md), [openapi.yaml](../contracts/openapi.yaml), [events.md](../contracts/events.md), [public-api.md](../contracts/public-api.md), [screens.md](../screens.md), [sad.md](../sad.md), [adr/](../adr/).

## Findings to fix

### A1 (stage 1, AC-227) — override check runs against the previous catalog
`CatalogRefresher.kt:87` publishes `ModelCatalogRefreshed` inside `store.replace`'s transaction; `holder.load()` runs only at `:98`, after `replace` returns. `OverrideValidator.onCatalogRefreshed` (`OverrideValidator.kt:27-38`, an `@ApplicationModuleListener`) reads `catalog.find()` from the in-memory `CatalogHolder`, so it usually sees the old snapshot (empty / NOT_LOADED on first start). A model that just left the catalog gets no warning until the next refresh 24 h later; a model that came back is still warned about. At startup `onReady` validates too, so every warning is logged twice. Existing tests miss it (`OverrideValidatorTest.kt:138` builds a new validator around the new catalog; `SystemProfilesIT` stubs `ModelCatalog`).
**Fix:** make the holder hold the new snapshot before the event can be consumed (e.g. set/reload the holder inside the `inTransaction` callback before publishing, or reload it right after commit and before publishing), and avoid the double log at startup. **Test:** an integration test wiring the real refresher + holder + validator where a refresh drops a shipped model → the warning appears on that refresh; and startup logs each warning exactly once.

### E2 (stage 2, AC-212, sad §11 "parse defensively") — one bad provider entry blocks every refresh
`ModelListParser.kt:60-67` doesn't bound `name` (column `VARCHAR(200)`) or `provider = id.substringBefore('/')` (column `VARCHAR(100)`), and `:36-41` doesn't de-duplicate ids (`PRIMARY KEY (model_id)`, `V202610031200__create_model_catalog.sql`). Any of these throws inside the single `replace` transaction (`CatalogSnapshotStore.kt:28-29`), the whole refresh rolls back and every 5-minute retry fails the same way.
**Fix:** in `parseOne` skip models whose name > 200 or provider > 100 chars, keep the first of duplicate ids, log each skipped model (id only, no secrets). **Test:** parser unit tests for each case.

## Definition of Done

A refresh that drops (or restores) a shipped override model logs (or stops logging) its warning on that same refresh, startup logs each warning once, and a provider list with an over-long name or provider or a repeated id still refreshes, skipping and logging only the bad entries; proved by an integration test over the real CatalogRefresher + CatalogHolder + OverrideValidator and by parser unit tests.

Test first: write the failing test(s) for each finding, run them, classify the first run (GOOD red / BAD red / false-pass / NON-red) and quote the failing line before production code. Never weaken an existing test except where a finding above says the expectation changes by an owner decision.
