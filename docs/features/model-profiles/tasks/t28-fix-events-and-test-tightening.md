---
id: T28
title: "Make agents events survive the publication registry, and tighten the T21–T24 tests and build wiring"
layer: "app"
deps: []
acs: ["AC-229", "AC-227", "AC-212", "AC-220"]
files_hint: ["backend/app/src/main/kotlin/telex/agents/ProfileRef.kt", "backend/app/src/main/kotlin/telex/agents/ModelCallFinished.kt", "backend/app/src/main/kotlin/telex/llm/internal/openrouter/ModelListParser.kt", "backend/app/src/test/kotlin/telex/llm/ModelListParserTest.kt", "backend/app/src/integrationTest/kotlin/telex/agents/", "backend/app/src/integrationTest/kotlin/telex/web/ModelsWriteLimitsApiIT.kt", "backend/app/build.gradle.kts", "build-logic/src/main/kotlin/", "docs/features/model-profiles/contracts/openapi.yaml"]
owner: "Anton Husiev"
estimate: "S"
origin: "review-2026-10-03 (per-task reviews of T21–T24)"
status: "todo"
---

# T28 — Make agents events survive the publication registry, and tighten the T21–T24 tests and build wiring

## Origin

Follow-up from the per-task reviews of T21–T24 during `/sdd:implement` after [review-2026-10-03.md](../_review/review-2026-10-03.md). Lines cite the code at commit `ca12a4e`; re-check before editing. Contracts: [events.md](../contracts/events.md), [public-api.md](../contracts/public-api.md), [openapi.yaml](../contracts/openapi.yaml), [data-model.md](../data-model.md).

## Findings to fix

### G1 (found by T23, stage 2, events.md / ADR-0004) — `ModelCallFinished` can't be deserialized by the publication registry
`ModelCallFinished.profile` is the sealed `ProfileRef` (`ProfileRef.kt:12`) with no Jackson type information. Now that the event is published inside the record transaction, a listener's publication is persisted; on republish (restart) Modulith logs `Cannot construct instance of telex.agents.ProfileRef`. **Fix:** give `ProfileRef` polymorphic type info (`@JsonTypeInfo`/`@JsonSubTypes`, or the repo's equivalent) or flatten the event payload (e.g. `systemProfileKey` / `customProfileId`), keeping events.md's payload rule (ids, keys, enums only). Check events.md and public-api.md for the declared shape and keep them in sync. **Test:** an integration test that serializes `ModelCallFinished` through the registry's serializer and reads it back equal, for both a system and a custom profile; and that an incomplete publication is republished without error.

### G2 (T21 review) — tests and parser
- `OverrideRefreshIT.kt:148`: the startup-once test calls `refresher.start()` then `validator.onReady()` by hand, so it can't catch a broken `@Order` (CatalogHolder 0, CatalogRefresher 1, OverrideValidator 2). Assert against the real context's startup (e.g. the warnings logged by the context's own ApplicationReadyEvent pass, or the beans' order).
- `ModelListParser.kt:126`: `perMtok()` keeps any non-negative price × 10^6, but `input_price_per_mtok`/`output_price_per_mtok` are `NUMERIC(14,6)`; a per-token price ≥ 100 overflows and fails the whole refresh. Skip and log such models; parser unit test.
- `OverrideRefreshIT.kt:88` (and `CatalogRefresherIT`): restore the logback logger level in tearDown.

### G3 (T23 review) — completed publication not proved
`ProfileCallsIT.kt:455` counts incomplete `ModelCallFinished` rows and expects 0, which passes with no row at all. Assert exactly one row whose `serialized_event` contains the call id and whose `completion_date IS NOT NULL`.

### G4 (T24 review) — lock tests and build wiring
- `ProfileCommands.kt:88`: no test for the lock in `delete()`. Add an ordered test: `setDefault` holds the lock, HTTP DELETE of the same profile waits and then returns 200 with `defaultReset=true` (no 500).
- `ModelsWriteLimitsApiIT.kt:259`: the race relies on a 1.5 s sleep. Wait until the PUT is actually blocked on the advisory lock (`pg_locks` / `pg_stat_activity.wait_event_type = 'Lock'` and `wait_event = 'advisory'`) before releasing.
- `ModelsWriteLimitsApiIT.kt:38`: unused `Callable` import; import `CountDownLatch` instead of the qualified name at :253.
- `backend/app/build.gradle.kts:45`: `-Xemit-jvm-type-annotations` set in a subproject script; CLAUDE.md says plugins are configured via convention plugins in `build-logic`. Move it to the Spring/Kotlin convention the app uses.
- `contracts/openapi.yaml:943`: T24 added `maxItems: 10` to `ChainInput` slots (transport cap; the domain still reports `slot-full` above 3). Add a one-line `description` on the field explaining that, so the contract change is self-documenting (the lead records it in the tracker).

## Definition of Done

ModelCallFinished (with its ProfileRef) round-trips through the Modulith event publication registry; the review minors on T21–T24 tests are fixed (order of startup validation, completed publication row, delete-side lock and lock-wait-based race, logger level restored, parser price overflow skipped); the -Xemit-jvm-type-annotations flag lives in a convention plugin; the ChainInput maxItems 10 contract change is recorded.

Test first where a finding is behavioural (G1, parser overflow, delete lock); test-quality findings are fixed by strengthening the test and showing it would fail on the broken variant where practical. Never weaken a test.
