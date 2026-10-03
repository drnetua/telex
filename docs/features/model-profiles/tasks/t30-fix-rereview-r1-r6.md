---
id: T30
title: "Keep the editor on a refused create, record calls after an interrupt, skip unreadable catalog entries, and match the event wire values"
layer: "app"
deps: ["T29"]
acs: ["AC-218", "AC-226", "AC-229", "AC-212", "AC-211"]
files_hint: ["frontend/src/api/models.ts", "frontend/src/pages/models/ProfileEditorModal.tsx", "frontend/src/pages/models/ProfileEditorModal.test.tsx", "backend/app/src/main/kotlin/telex/agents/internal/call/ProfileCallService.kt", "backend/app/src/main/kotlin/telex/llm/internal/openrouter/ModelListParser.kt", "backend/app/src/test/kotlin/telex/llm/ModelListParserTest.kt", "backend/app/src/main/kotlin/telex/agents/ModelCallFinished.kt", "backend/app/src/main/kotlin/telex/llm/ModelSlotKind.kt", "backend/app/src/integrationTest/kotlin/telex/agents/call/", "docs/features/model-profiles/tasks/tracker.md"]
owner: "Anton Husiev"
estimate: "S"
origin: "re-review 2026-10-03 round 2"
status: "done"
---

# T30 — Keep the editor on a refused create, record calls after an interrupt, skip unreadable catalog entries, and match the event wire values

## Origin

Findings R1–R6 of [review-2026-10-03-r2.md](../_review/review-2026-10-03-r2.md) (code at `b5f2c9d`). Contracts: [events.md](../contracts/events.md), [screens.md](../screens.md) SCR-34, [spec.md](../spec.md) §5.

## Findings to fix

### R1 (stage 1, AC-218/AC-226, SCR-34 `save refused`) — a refused create wipes the editor
`frontend/src/api/models.ts:187`: `if (dropsDrafts) client.removeQueries({ queryKey: draftsKey })` runs in the shared `onSettled`, so also on errors. The editor's `notify()` re-renders, `useQuery` rebuilds an empty draft, and `ProfileEditorModal.tsx:90-93` (`draft.usable ? draft.data : undefined`) loses the data while the `usable` latch keeps `loaded` true. `EditorForm` is replaced (typed name and chains gone) and a new draft GET follows; if that also returns 409 the modal stays on the 3-row skeleton at `/settings/models/profiles/new`. **Fix:** drop/mark drafts only on success, as stale rather than removed (`invalidateQueries({ queryKey: draftsKey, refetchType: "none" })` — the H2 test accepts `isInvalidated`); make the hook keep the last usable data, not only the flag. **Tests (RED first):** POST 409 `profile-limit-reached`, POST 409 `ai-not-configured`, and a non-field 400 (`saveFailed`): the dialog stays open per SCR-34, the typed name survives, no extra draft request. Keep the H2 test green.

### R2 (stage 2, AC-229) — the record is lost after an interrupt on a virtual thread
`FallbackLoop.kt:63` restores the interrupt flag; `ProfileCallService.kt:74-75` then writes the record on that interrupted thread. With `spring.threads.virtual.enabled: true`, blocking socket I/O fails with `SocketException: Closed by interrupt`, `runCatching` swallows it, the `timeout` attempt is never written and the pooled connection is broken. **Fix:** in `ProfileCallService`, `val wasInterrupted = Thread.interrupted()` before writing, and re-set the flag after the write and publish. **Test:** an IT that runs `ProfileCalls.call` on a virtual thread, interrupts it during a stalled attempt (WireMock `withFixedDelay`), and asserts a `model_call` row with a `timeout` attempt and the flag set on return.

### R3 (stage 2, AC-212) — one unreadable entry fails the refresh
Jackson 3 (`jackson-databind` 3.1.5): `asInt()` on `"context_length": 10000000000` and `asString()` on `"input_modalities": [{}]` throw `JsonNodeException` (`ModelListParser.kt:123,134`); `parseOne` (called at `:46`) doesn't catch, so `parse()` throws. **Fix:** wrap `parseOne(node)` so any exception becomes a `SkippedModel(id, "unreadable entry")` (logged by id); read `context_length` only when `canConvertToInt()` (otherwise null/unknown as for a missing value — follow the existing missing-value handling), and modalities only from string nodes. **Tests:** parser unit tests for both inputs: only that model is skipped or degraded, the others are kept.

### R4 (stage 2, AC-229, events.md) — event wire values — decision: **code follows events.md**
The stored `ModelCallFinished` JSON has `"slot":"TEXT","outcome":"NO_MODEL_ANSWERED"`; `events.md:82-83` documents `text` and `no-model-answered` (the profile key is already lowercase via `@get:JsonValue` on `SystemProfileKey.wire`). **Fix:** serialize `ModelSlotKind` by its wire value and `ModelCallOutcome` by the kebab value events.md lists (`@JsonValue` / `@JsonCreator` or the repo's existing pattern), keeping deserialization of the new form. Check that no REST DTO serializes these enums directly in a way that would change the API (web maps through its own DTOs — confirm). **Test:** extend `ModelCallFinishedSerializationIT`'s shape test to assert `slot` and `outcome`, and the round trip.

### R5 (stage 2) — root logger left at DEBUG
`ProfileCallsIT.kt:178` sets the root logger to DEBUG; `tearDown` (`:183`) never restores it. Save and restore it as `OverrideRefreshIT` and `CatalogRefresherIT` now do.

### R6 (stage 2, AC-211, docs) — tracker wording
`tasks/tracker.md:42` ("Image-slot prices show 'Price unknown' until one is chosen") and `:50` ("AC-211's 'per image' price isn't asserted (see T2)") contradict the amended AC-211. Reword: image models show per-1M-token prices (spec §1 deviation, review 2026-10-03), `pricePerImage` is reserved and null, asserted by `ModelsPage.test.tsx` "AC-211: an image-output model…".

## Definition of Done

A POST 409 (profile-limit-reached, ai-not-configured) or a non-field 400 on create keeps the dialog open with the typed values and sends no extra draft request; a call interrupted on a virtual thread still writes its model_call row with a timeout attempt; an out-of-range context_length or a non-string modality skips only that model; the stored ModelCallFinished JSON uses the events.md wire values for slot and outcome; ProfileCallsIT restores the root logger level; the tracker describes per-1M-token prices for image models.

Gate: `cd frontend && pnpm run check`; `./gradlew :backend:app:test spotlessCheck detekt`; `./gradlew :backend:app:integrationTest --tests 'telex.agents.call.*' --tests 'telex.llm.*'`.
