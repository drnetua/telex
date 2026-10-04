---
id: T24
title: "Refuse only system display names, serialize default against delete, cap request sizes, and prove saves during an outage"
layer: "app"
deps: []
acs: ["AC-214", "AC-220", "AC-222", "AC-212"]
files_hint: ["backend/app/src/main/kotlin/telex/agents/internal/profile/ProfileRules.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/ProfileCommands.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/FieldProblems.kt", "backend/app/src/main/kotlin/telex/web/api/ModelsDtos.kt", "backend/app/src/test/kotlin/telex/agents/internal/profile/ProfileRulesTest.kt", "backend/app/src/integrationTest/kotlin/telex/web/", "backend/app/src/integrationTest/kotlin/telex/agents/profile/"]
owner: "Anton Husiev"
estimate: "S"
origin: "review-2026-10-03"
status: "todo"
---

# T24 — Refuse only system display names, serialize default against delete, cap request sizes, and prove saves during an outage

## Origin

Follow-up task from the independent review — [review-2026-10-03.md](../_review/review-2026-10-03.md). Each finding below cites `file:line` as of commit `9e411fd`; re-check the lines before editing. The ACs are in [spec.md](../spec.md) §5 (source of truth); contracts in [data-model.md](../data-model.md), [openapi.yaml](../contracts/openapi.yaml), [events.md](../contracts/events.md), [public-api.md](../contracts/public-api.md), [screens.md](../screens.md), [sad.md](../sad.md), [adr/](../adr/).

## Findings to fix

### D2 (stage 1, AC-214) — name rule also reserves the keys — decision: **match the spec**
`ProfileRules.kt:51-56` compares the name with both `displayName` and `it.wire`, so "Fast", "balanced", "careful" are refused (`ProfileRulesTest.kt:97` asserts it). AC-214 only forbids the system profile *names* ("Fast and cheap", "Balanced", "Careful"); "Balanced" stays refused because it is a display name. **Fix:** compare only against display names (case-insensitive, as now); change `ProfileRulesTest.kt:97` to assert "Fast" is accepted and "Fast and cheap" refused (this is a spec-directed change of the expectation, approved by the owner in review — not a weakening). Check the frontend's client-side check (`frontend/src/pages/models/ProfileEditorModal.tsx` ~204) already compares display names only; leave the UI file alone otherwise.

### E3 (stage 2, AC-220/AC-222) — default vs delete race → 500
`ProfileCommands.kt:94-108` (`setDefault`) reads the profile then upserts `default_model_profile` without a lock; a delete committing in between makes the composite FK `default_model_profile_custom_profile_fk` fail → `DataIntegrityViolationException` → 500 from ProblemHandler's catch-all. **Fix:** take the same Owner advisory lock (`profiles.lockOwner(owner)`) at the start of `setDefault` and `delete`. **Test:** a race integration test (or a deterministic ordering test) showing 404 `not-found`.

### E4 (stage 2, openapi.yaml) — request sizes not bounded, bad ids echoed back
`ModelsDtos.kt:121-134`: `name` and model ids lack `@Size`; slot arrays have no limit; each bad id becomes a `FieldProblem` whose message repeats the raw id (`FieldProblems.kt:16,37`). **Fix:** bound fields per `contracts/openapi.yaml` (read it: name/model id `maxLength`, slot array `maxItems`), with `@Valid` on nested DTOs, answered as `400 validation-failed` in the repo's problem format; keep domain rules reporting `slot-full` within the cap. **Test:** `ModelsWriteApiIT` cases for an over-long id and an oversized array.

### A4a (stage 1, AC-212, sad §10 "Provider outage") — no save under outage
**Test:** a `ModelsWriteApiIT` case: create and update succeed with the catalog in `UPDATE_FAILED` and a non-empty snapshot.

## Definition of Done

Custom profiles named Fast/Balanced/Careful (keys) are accepted while the display names stay refused; setDefault racing delete answers 404 not-found, never 500; request bodies are bounded per openapi.yaml with validation-failed; create and update succeed while the catalog is UPDATE_FAILED with a non-empty snapshot.

Test first: write the failing test(s) for each finding, run them, classify the first run (GOOD red / BAD red / false-pass / NON-red) and quote the failing line before production code. Never weaken an existing test except where a finding above says the expectation changes by an owner decision.
