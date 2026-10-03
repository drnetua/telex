---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "S"
ticket: "E10 model-profiles"
---

# 0003 — Run the in-call fallback as a client-side loop in `llm`, one attempt per model

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

When a slot's model is missing or fails, the same request must be answered by the next model in its Fallback Chain, without the caller retrying (AC-224). A content refusal or an invalid request must stop the chain (AC-228). Every model tried and its outcome must be recorded (AC-229). OpenRouter can fall back on its own when one request carries a `models` array. This choice fixes the port that every future caller sees (triage, agents, E14 runs), and the shape of the call record.

## Decision drivers

- AC-224 / AC-228: classify each failure. Unavailable, provider error, rate limit, timeout and request too large move on. Content refusal and invalid request stop.
- AC-229: per-attempt outcomes go into the call record.
- Spec §6 NFR "In-call fallback": a model that doesn't answer within the attempt timeout (default 60 s) counts as failed.
- BYOK and other providers are coming in E27, so the fallback should not depend on one provider's routing features.

## Considered options

1. **Client-side loop in `llm`.** For each model id in order, `llm` skips it if it isn't in the Model Catalog (recorded as `missing`). Otherwise it sends one request with the attempt timeout, classifies the outcome, and either returns, moves on or stops. It returns the answer together with the list of attempts.
2. **OpenRouter native fallback.** One request with `models: [A, B, C]`, and OpenRouter switches models server-side.

## Decision outcome

**Chosen:** option 1. Option 2 also falls back after a content refusal, which contradicts AC-228. It returns only the final model, so there is nothing to record per attempt for AC-229, and it has no per-attempt timeout. Option 1 meets all three criteria as written and keeps `llm` provider-neutral.

## Consequences

**Positive**
- The outcome rules live in one tested place (`llm`), driven by a WireMock fake that can fail, stall and refuse.
- Callers get a typed result: the answer, the model that answered, a fallback flag, and the attempts with their outcomes. If no model could answer, they get a typed "no model in the slot could answer" failure that lists every attempt (AC-228).

**Negative**
- Each attempt is a full round trip. If model A stalls, the caller waits up to the attempt timeout (60 s by default) before model B is tried.
- Around 150 lines of our own retry/classification logic to maintain, and the mapping of provider errors to outcomes may need tuning as OpenRouter's error bodies change.

**Neutral**
- The attempt timeout is configuration (`telex.llm.attempt-timeout`, default 60 s), which resolves the spec §8 question.
- The next request always starts again from the first model. There is no circuit breaker in E10 (§11).

## Links

- Spec: [[../spec.md]] US-82, AC-10, AC-224, AC-228, AC-229
- SAD: [[../sad.md]] §4, §6, §8
- Related ADR: [[0002-profiles-in-agents-llm-thin-acl]], [[0004-call-records-in-agents-table]]
