---
id: T13
title: "Answer profile slot calls through ProfileCalls and record every call without content"
layer: "app"
deps: ["T6", "T11"]
blocks: ["T20"]
acs: ["AC-10", "AC-223", "AC-224", "AC-228", "AC-229"]
files_hint: ["backend/app/src/main/kotlin/telex/agents/ProfileCalls.kt", "backend/app/src/main/kotlin/telex/agents/ModelCallId.kt", "backend/app/src/main/kotlin/telex/agents/ModelCallFinished.kt", "backend/app/src/main/kotlin/telex/agents/internal/call/", "backend/app/src/integrationTest/kotlin/telex/agents/call/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T13 — Answer profile slot calls through ProfileCalls and record every call without content

## Place in the sequence

- **Blocked by:** T6 — Call OpenRouter for text, vision and image through the provider port and classify its errors, T11 — Serve the catalog view, the profile list with picker data, one profile and a new-profile draft · **Blocks:** T20 — Document the AI settings in the README and add the real-call smoke check per slot · **Wave:** 6 — needs the provider adapter (T6) and profile resolution (T11).
- **Lane:** own lane (`telex/agents/internal/call/`).

## Why (user story)

> **As an** Owner
> **I want** teleX to switch to the next model in the chain when a model is gone or fails, and to show me while a profile's main model is missing
> **So that** a vanished or broken model doesn't stop my helpers and a lasting switch doesn't surprise me
>
> — `spec.md §4, US-82, verbatim` · full text: [spec.md](../spec.md)

This task gives the rest of teleX one way to get an answer from a profile's slot that survives a vanished or failing model, and leaves a content-free trace of every call.

## Inlined context

> **Chosen:** option 1. It puts the record in the same module as the future Runs, so run details and the KPI read it without crossing modules. It is written synchronously, so it is visible as soon as the call returns.
>
> — `adr/0004 §Decision outcome, abridged` · full text: [ADR-0004](../adr/0004-call-records-in-agents-table.md)

> | No model left, or an empty vision or image slot → `Failed(NO_MODEL_AVAILABLE)` with no provider call, and **never** the model of another slot. | AC-223 |
> | `Custom` id not among `ownerId`'s profiles → `Failed(PROFILE_NOT_FOUND)`, same as a missing one. No call record, because there is no profile to record. | AC-222 |
> | `fallback = true` when any model before `answeredBy` was skipped or failed, including a skipped missing main model. | AC-229 |
> | Every call except `PROFILE_NOT_FOUND` writes one `model_call` row with its attempts and then publishes `ModelCallFinished` (events.md). A failed write is logged and doesn't change the result. | AC-229, ADR-0004 |
> | Callers must already have passed consent and private-zone checks: this port doesn't see message context. | sad §11 |
>
> — `public-api.md §Semantics, abridged` · full text: [public-api.md](../contracts/public-api.md)

> | Call record write | Every profile call gets one record: after `llm` returns or fails, or with reason `no-model-available` when the slot has no usable model (skipped models recorded as `missing`). A failed record write is logged and does not turn a successful answer into an error | feature ADR-0004 |
>
> — `sad.md §8, Call record write, verbatim` · full text: [sad.md](../sad.md)

> counter `telex.model.calls{slot,outcome=answered|failed,fallback=true|false}`, timer `telex.model.attempt{outcome}`. No model output, Owner ids or key material in tags.
>
> — `sad.md §7, Monitoring, abridged` · full text: [sad.md](../sad.md)

> Data classification internal. teleX never stores or logs request or answer text (spec §6.1). A profile call passes the caller's request only to the model provider and returns the answer to the caller, and the call record is content-free by construction (AC-229). Nothing an Owner does in E10 triggers a model call.
>
> The provider key is an installation secret. It is set only through installation settings (environment), never returned to the browser, never logged (spec §6.1).
>
> — `sad.md §2, Regulatory / external, abridged` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Table | Columns written | Change |
|---|---|---|
| `model_call` | `id` (UUIDv7), `owner_id`, `system_profile_key` \| `custom_profile_id`, `slot`, `outcome` (`answered`, `no-model-available`, `no-model-answered`, `ai-not-configured`), `answered_by_model_id` (iff answered), `fallback`, `started_at`, `finished_at` — insert only | write |
| `model_call_attempt` | `model_call_id`, `position` 1–3, `model_id`, `outcome` — one row per attempt; zero for an empty slot or `ai-not-configured` | write |

— `data-model.md §Entities, agents — call records, abridged` · full text: [data-model.md](../data-model.md)

## API contract

In-process port at the `agents` root (public-api.md — names may move, shapes and semantics may not):

```kotlin
interface ProfileCalls {
    fun call(ownerId: OwnerId, profile: ProfileRef, slot: ModelSlotKind, request: SlotRequest): ProfileCallResult
}
// ProfileCallResult.Answered(callId, answer, answeredBy, fallback, attempts) | Failed(callId, reason, attempts)
enum class SlotFailure { NO_MODEL_AVAILABLE, NO_MODEL_ANSWERED, AI_NOT_CONFIGURED, PROFILE_NOT_FOUND }
```

Event `telex.agents.ModelCallFinished(callId, ownerId, profile, slot, outcome, answeredByModelId, fallback)` — published right after the row is written; not published when the write fails.

— `public-api.md, ProfileCalls, abridged` + `events.md, agents.model-call-finished.v1, abridged` · full text: [public-api.md](../contracts/public-api.md)

## Acceptance criteria

### AC-10 — error (US-82)

> **Given** a profile whose text slot chain is model A then model B, and model A has left the Model Catalog
> **When** the Owner opens the Models page, and when teleX makes a text call with that profile
> **Then** the profile and its text slot show "Main model unavailable. Using model B for now.", the price estimate reflects model B, the call is answered by model B, and once model A returns to the catalog the warning disappears and model A is used again
>
> — `spec.md §5, AC-10, verbatim` · full text: [spec.md](../spec.md)

### AC-223 — error (US-82)

> **Given** a profile with a slot that has no model left in the Model Catalog, or an empty vision or image slot
> **When** the Owner opens the Models page, and when teleX needs that slot for a call
> **Then** the slot shows that no model is available for it, or "Not used" when it is empty (for a custom profile, with a prompt to pick another model; for a system profile, with a suggestion to choose another profile), and the call fails with that plain reason instead of being sent anywhere — never to the model of another slot; when it is the text slot, the picker shows the profile as "No model available for text" with no price, it can't be chosen as a new default, and Owners who already have it as their default keep it and see a warning suggesting another profile
>
> — `spec.md §5, AC-223, verbatim` · full text: [spec.md](../spec.md)

### AC-224 — cross-context (US-82)

> **Given** a profile whose text slot chain is model A then model B, both in the Model Catalog
> **When** another part of teleX asks the profile's text slot for an answer, and model A is unavailable, returns a provider error, hits a rate limit, doesn't answer in time, or can't take a request that large
> **Then** the same request is answered by model B without the caller retrying, and the result names model B as the model that answered and that a fallback happened; the next request starts again from model A
>
> — `spec.md §5, AC-224, verbatim` · full text: [spec.md](../spec.md)

### AC-228 — error (US-82)

> **Given** a profile whose text slot chain is model A then model B
> **When** another part of teleX asks the slot for an answer and both models fail, or model A refuses the request because of its content, or the request itself is invalid
> **Then** the request fails without trying further models after a content refusal or an invalid request, and the caller is told that no model in the text slot could answer, with each model tried and its reason
>
> — `spec.md §5, AC-228, verbatim` · full text: [spec.md](../spec.md)

### AC-229 — cross-context (US-82)

> **Given** an Owner whose AI work makes calls through a profile, some answered by the main model, some after a fallback and some failing
> **When** the calls finish
> **Then** each call is recorded without any message content: when it happened, the Owner, profile, slot, every model tried with its outcome, the model that answered (if any) and whether a fallback happened (skipping a main model that is missing from the catalog counts as one), so that run details and the fallback KPI read from one place
>
> — `spec.md §5, AC-229, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `ProfileCalls`, `SlotRequest`, `SlotAnswer`, `ProfileCallResult`, `SlotFailure`, agents-side `Attempt`/`AttemptOutcome`, `ModelCallId` at the `agents` root — `backend/app/src/main/kotlin/telex/agents/`
- [ ] `ProfileCallService`: resolve ref (system via T9, custom via T10 by owner), slot chain, skip unusable as `MISSING`, call `llm.ModelCalls`, map the result, write the record, publish, count metrics — `backend/app/src/main/kotlin/telex/agents/internal/call/`
- [ ] `CallRecordRepository` (insert-only + read by id for tests) — `backend/app/src/main/kotlin/telex/agents/internal/call/`
- [ ] Integration tests with WireMock: A missing → B answers (fallback true); A 429 → B; A refuses → stop; empty vision slot; no key; foreign custom id; record write failure keeps the answer — `backend/app/src/integrationTest/kotlin/telex/agents/call/`

## Edge cases

| Case | Behaviour |
|---|---|
| Empty image slot asked for an answer | `Failed(NO_MODEL_AVAILABLE)`, zero attempts, a record — never the text slot's model |
| Every chain model missing | `Failed(NO_MODEL_AVAILABLE)`, one `missing` attempt per model, no provider call |
| Main model missing, backup answers | `Answered(answeredBy = B, fallback = true)`; the record counts it as a fallback |
| Another Owner's custom profile id | `Failed(PROFILE_NOT_FOUND)`, no record |
| The record insert fails | Logged; the answer is returned; no `ModelCallFinished` |
| Request text in the record, log or event | Never — no column or field can hold it |

## Definition of Done

- [ ] the ProfileCalls integration tests pass for every semantics row
- [ ] a test asserts no request or answer text reaches `model_call*` or the logs
- [ ] every Hard Rule inlined above still holds
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
