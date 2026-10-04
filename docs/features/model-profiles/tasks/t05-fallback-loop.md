---
id: T5
title: "Build the in-call fallback loop in llm with one attempt per model, a per-attempt timeout and outcome classification"
layer: "app"
deps: ["T2"]
blocks: ["T6"]
acs: ["AC-10", "AC-224", "AC-228"]
files_hint: ["backend/app/src/main/kotlin/telex/llm/ModelCalls.kt", "backend/app/src/main/kotlin/telex/llm/Attempt.kt", "backend/app/src/main/kotlin/telex/llm/internal/call/", "backend/app/src/main/kotlin/telex/llm/internal/call/ModelProvider.kt", "backend/app/src/test/kotlin/telex/llm/call/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T5 — Build the in-call fallback loop in llm with one attempt per model, a per-attempt timeout and outcome classification

## Place in the sequence

- **Blocked by:** T2 — Define the llm catalog value types, the slot-fit rule and the OpenRouter model-list parser · **Blocks:** T6 — Call OpenRouter for text, vision and image through the provider port and classify its errors · **Wave:** 2 — needs the llm value types (T2); parallel with T3.
- **Lane:** **compile-coupled pair with T6** — both list `telex/llm/internal/call/ModelProvider.kt`; `implement` serializes them and may close them with one gate (the `ModelCalls` bean needs a provider bean to boot).

## Why (user story)

> **As an** Owner
> **I want** teleX to switch to the next model in the chain when a model is gone or fails, and to show me while a profile's main model is missing
> **So that** a vanished or broken model doesn't stop my helpers and a lasting switch doesn't surprise me
>
> — `spec.md §4, US-82, verbatim` · full text: [spec.md](../spec.md)

This task is the in-call fallback itself: a vanished or failing model is passed over inside the same request, and every attempt is reported back.

## Inlined context

> **Chosen:** option 1. Option 2 also falls back after a content refusal, which contradicts AC-228. It returns only the final model, so there is nothing to record per attempt for AC-229, and it has no per-attempt timeout. Option 1 meets all three criteria as written and keeps `llm` provider-neutral.
>
> — `adr/0003 §Decision outcome, verbatim` · full text: [ADR-0003](../adr/0003-client-side-fallback-loop-in-llm.md)

> | Model call failures | Typed results, not exceptions, across the `llm` and `agents` APIs. Per-attempt outcomes: `missing`, `unavailable`, `provider-error`, `rate-limited`, `timeout`, `too-large` (move on); `content-refused`, `invalid-request` (stop). Slot failures: `no-model-available`, `no-model-answered` (with attempts), `ai-not-configured` | feature ADR-0003 |
> | Attempt timeout | `telex.llm.attempt-timeout`, 60 s by default. […] | feature ADR-0003 |
>
> — `sad.md §8, Model call failures + Attempt timeout, abridged` · full text: [sad.md](../sad.md)

> | The chain is the slot's models in position order. Models not in the catalog, or (system overrides) not able to do the slot's job, are skipped as `MISSING` with no provider call. | AC-10, AC-227, sad §4 |
> | Each remaining model gets one attempt within `telex.llm.attempt-timeout` (60 s). A move-on outcome tries the next model with the same request, and the caller never retries. | AC-224, ADR-0003 |
> | `CONTENT_REFUSED` or `INVALID_REQUEST` stops at once: `Failed(NO_MODEL_ANSWERED)`, no further models. | AC-228 |
> | All models fail → `Failed(NO_MODEL_ANSWERED)` with every attempt. | AC-228 |
> | No provider key → `Failed(AI_NOT_CONFIGURED)`, zero attempts. | AC-226 |
> | The next call starts again from position 1. There is no memory of earlier failures. | AC-224 |
>
> — `public-api.md §Semantics, abridged` · full text: [public-api.md](../contracts/public-api.md)

> | In-call fallback | the next model is tried within the same request; a model that doesn't answer within the attempt timeout (default 60 s, §8) counts as failed | integration test with a provider fake that fails and stalls |
>
> — `spec.md §6, NFR In-call fallback, verbatim` · full text: [spec.md](../spec.md)

> Kotlin 2.4.10 on JDK 25 with virtual threads on (`spring.threads.virtual.enabled`). A model call blocks its virtual thread for up to the attempt timeout.
>
> — `sad.md §2, Technical constraints, verbatim` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — `llm` root API (sad §5): `ModelCalls.call(chain: List<ModelId>, slot, request) → answer + attempts | failure`. `llm`-side `AttemptOutcome` (with `movesOn`) and `Attempt(modelId, outcome)` mirror the `agents` port in public-api.md:

```kotlin
enum class AttemptOutcome(val movesOn: Boolean) {
    ANSWERED(false),
    MISSING(true), UNAVAILABLE(true), PROVIDER_ERROR(true), RATE_LIMITED(true), TIMEOUT(true), TOO_LARGE(true),
    CONTENT_REFUSED(false), INVALID_REQUEST(false),
}
```

`internal/call/ModelProvider` is the port T6 implements: one attempt for one model and one slot request, returning an answer or a classified outcome.

— `public-api.md, ProfileCalls / AttemptOutcome, abridged` · full text: [public-api.md](../contracts/public-api.md)

## Acceptance criteria

### AC-10 — error (US-82)

> **Given** a profile whose text slot chain is model A then model B, and model A has left the Model Catalog
> **When** the Owner opens the Models page, and when teleX makes a text call with that profile
> **Then** the profile and its text slot show "Main model unavailable. Using model B for now.", the price estimate reflects model B, the call is answered by model B, and once model A returns to the catalog the warning disappears and model A is used again
>
> — `spec.md §5, AC-10, verbatim` · full text: [spec.md](../spec.md)

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

## Checklist

- [ ] `Attempt`, `AttemptOutcome`, the `llm` slot request / answer types and `ModelCallResult` at the `llm` root — `backend/app/src/main/kotlin/telex/llm/`
- [ ] `ModelProvider` port (one attempt, typed outcome, never throws for a model failure) — `backend/app/src/main/kotlin/telex/llm/internal/call/ModelProvider.kt`
- [ ] `FallbackLoop` implementing `ModelCalls`: skip models absent from the `ModelCatalog` as `MISSING`, enforce `telex.llm.attempt-timeout` per attempt, stop or move on per outcome, `AI_NOT_CONFIGURED` with zero attempts when no key — `backend/app/src/main/kotlin/telex/llm/internal/call/`
- [ ] Unit tests with a scripted fake provider and a short timeout — `backend/app/src/test/kotlin/telex/llm/call/`

## Edge cases

| Case | Behaviour |
|---|---|
| Main model missing from the catalog, backup answers | Attempts `[A: MISSING, B: ANSWERED]`, answered by B (AC-10) |
| A stalls past the attempt timeout | A recorded `TIMEOUT`, B tried with the same request |
| A refuses the content | `Failed(NO_MODEL_ANSWERED)`, attempts `[A: CONTENT_REFUSED]`, B never called |
| Every model fails with move-on outcomes | `Failed(NO_MODEL_ANSWERED)` with every attempt |
| Empty chain | Not the loop's case — `agents` answers `NO_MODEL_AVAILABLE` before calling (T13) |
| Provider throws an unexpected exception | Classified `PROVIDER_ERROR` (move on), never propagated |

## Definition of Done

- [ ] the fallback-loop unit tests pass for every outcome
- [ ] together with T6 the app context boots (compile-coupled gate)
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
