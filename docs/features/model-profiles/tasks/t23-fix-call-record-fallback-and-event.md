---
id: T23
title: "Record the fallback flag on failed calls, publish ModelCallFinished with the record, and type ModelCallId"
layer: "app"
deps: []
acs: ["AC-229", "AC-224"]
files_hint: ["backend/app/src/main/kotlin/telex/agents/ProfileCalls.kt", "backend/app/src/main/kotlin/telex/agents/internal/call/", "backend/app/src/integrationTest/kotlin/telex/agents/call/"]
owner: "Anton Husiev"
estimate: "S"
origin: "review-2026-10-03"
status: "todo"
---

# T23 — Record the fallback flag on failed calls, publish ModelCallFinished with the record, and type ModelCallId

## Origin

Follow-up task from the independent review — [review-2026-10-03.md](../_review/review-2026-10-03.md). Each finding below cites `file:line` as of commit `9e411fd`; re-check the lines before editing. The ACs are in [spec.md](../spec.md) §5 (source of truth); contracts in [data-model.md](../data-model.md), [openapi.yaml](../contracts/openapi.yaml), [events.md](../contracts/events.md), [public-api.md](../contracts/public-api.md), [screens.md](../screens.md), [sad.md](../sad.md), [adr/](../adr/).

## Findings to fix

### A3 (stage 1, AC-229) — failed calls always record `fallback = false`
`ProfileCallService.kt:69`: `answered?.fallback ?: false`. "A provider-error, then B rate-limited" and "A missing, then B missing" both store `false`. `data-model.md:207` defines the flag as "True when a later model answered **or was needed**, including a skipped missing main model".
**Fix:** for failed calls set `fallback = true` when more than one attempt was made or the first attempt's outcome is `MISSING`. Keep the metric tag consistent. **Test:** assert the flag in `ProfileCallsIT` "both models fail" (`:349-364`) and a missing-then-failed case.

### E1 (stage 2, AC-229, `contracts/events.md:18`) — `ModelCallFinished` published outside any transaction
`ProfileCallService.kt:71-91` isn't transactional; `CallRecordRepository.insert` (`:34`) commits its own transaction, then `events.publishEvent(...)` runs with none active. `@ApplicationModuleListener` is a `@TransactionalEventListener` without `fallbackExecution`, so listeners are skipped; Modulith stores an incomplete publication delivered only after a restart (`republish-outstanding-events-on-restart`). `@RecordApplicationEvents` in `ProfileCallsIT` hides it.
**Fix:** write the record and publish the event in one transaction (e.g. an `inTransaction` callback on `insert`, like `CatalogSnapshotStore.replace`). Keep the model call itself outside the transaction and keep "a failed write is logged and doesn't change the result". **Test:** an integration test with a test `@ApplicationModuleListener` (or Modulith `Scenario`) proving delivery and that the `event_publication` row completes.

### E6 (stage 2, ADR-0003) — `ModelCallId` isn't a `TypedId`
`ProfileCalls.kt:10-13`: `value class ModelCallId(val value: UUID)`. **Fix:** `@JvmInline value class ModelCallId(override val value: UUID) : TypedId`.

### A4b (stage 1, sad §10 QG-1, AC-224) — stalled model not tested through ProfileCalls
**Test:** a `ProfileCallsIT` case with WireMock `withFixedDelay` on model A and a short `telex.llm.attempt-timeout`, asserting attempt rows `timeout` then `answered`, `answered_by_model_id` = B, `fallback = true`.

## Definition of Done

A failed call where a later model was tried or the main model was missing stores fallback = true; ModelCallFinished is published inside the record transaction and a test proves an @ApplicationModuleListener receives it and the publication completes; ModelCallId is a TypedId; a stalled first model through ProfileCalls records attempts timeout then answered with fallback = true.

Test first: write the failing test(s) for each finding, run them, classify the first run (GOOD red / BAD red / false-pass / NON-red) and quote the failing line before production code. Never weaken an existing test except where a finding above says the expectation changes by an owner decision.
