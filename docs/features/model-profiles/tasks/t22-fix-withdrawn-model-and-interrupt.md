---
id: T22
title: "Fall back past a withdrawn model id and keep the fallback loop from throwing on interrupt"
layer: "infra"
deps: []
acs: ["AC-224", "AC-229"]
files_hint: ["backend/app/src/main/kotlin/telex/llm/internal/openrouter/OpenRouterErrors.kt", "backend/app/src/main/kotlin/telex/llm/internal/call/FallbackLoop.kt", "backend/app/src/test/kotlin/telex/llm/OpenRouterErrorsTest.kt", "backend/app/src/test/kotlin/telex/llm/call/FallbackLoopTest.kt", "backend/app/src/integrationTest/kotlin/telex/llm/"]
owner: "Anton Husiev"
estimate: "S"
origin: "review-2026-10-03"
status: "todo"
---

# T22 — Fall back past a withdrawn model id and keep the fallback loop from throwing on interrupt

## Origin

Follow-up task from the independent review — [review-2026-10-03.md](../_review/review-2026-10-03.md). Each finding below cites `file:line` as of commit `9e411fd`; re-check the lines before editing. The ACs are in [spec.md](../spec.md) §5 (source of truth); contracts in [data-model.md](../data-model.md), [openapi.yaml](../contracts/openapi.yaml), [events.md](../contracts/events.md), [public-api.md](../contracts/public-api.md), [screens.md](../screens.md), [sad.md](../sad.md), [adr/](../adr/).

## Findings to fix

### A2 (stage 1, AC-224) — a withdrawn model id stops the chain
OpenRouter answers a model id it no longer serves with `400 {"error":{"message":"<id> is not a valid model ID"}}`. `OpenRouterErrors.classifyBadRequest` (`OpenRouterErrors.kt:52-57`) only matches the too-large and refusal words, so this becomes `INVALID_REQUEST` (`movesOn=false`) and model B is never tried — exactly AC-224's "model A is unavailable" case, possible for up to 24 h between catalog refreshes. The `noProvider` patterns are only checked for 502/503 (`:38-39`).
**Fix:** in the 400 branch, map messages containing "not a valid model", "model not found" / "no such model", "no endpoints" to `UNAVAILABLE`, before the `INVALID_REQUEST` default (refusal and too-large matches keep priority). Record the change in the error table line of `tasks/tracker.md` §Deviations is done by the lead — don't edit tracker.md. **Test:** `OpenRouterErrorsTest` cases + a move-on case in `OpenRouterProviderIT`'s parameter set.

### E5 (stage 2, AC-229) — interrupt escapes the loop
`FallbackLoop.kt:56-63`: `future.get(...)` can throw `InterruptedException`; it isn't caught, so it escapes `ProfileCallService.call` before the call record is written (contract in `ModelCalls.kt:59`: "Never throws for a model failure") and the provider task isn't cancelled.
**Fix:** catch `InterruptedException`, `future.cancel(true)`, restore the interrupt flag (`Thread.currentThread().interrupt()`), and return a `Failure` with the attempts so far (the current attempt as `TIMEOUT`), stopping the loop. **Test:** `FallbackLoopTest` with an interrupted caller thread.

## Definition of Done

A 400 whose error message says the model id is not valid / not found / has no endpoints is classified UNAVAILABLE and the loop tries the next model (unit + OpenRouterProviderIT move-on case); an interrupted caller gets a Failure result with the in-flight attempt cancelled and the interrupt flag restored, never an exception.

Test first: write the failing test(s) for each finding, run them, classify the first run (GOOD red / BAD red / false-pass / NON-red) and quote the failing line before production code. Never weaken an existing test except where a finding above says the expectation changes by an owner decision.
