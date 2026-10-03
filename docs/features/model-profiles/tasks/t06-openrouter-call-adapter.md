---
id: T6
title: "Call OpenRouter for text, vision and image through the provider port and classify its errors"
layer: "infra"
deps: ["T4", "T5"]
blocks: ["T13"]
acs: ["AC-224", "AC-228"]
files_hint: ["backend/app/src/main/kotlin/telex/llm/internal/openrouter/", "backend/app/src/main/kotlin/telex/llm/internal/call/ModelProvider.kt", "gradle/libs.versions.toml", "backend/app/build.gradle.kts", "backend/app/src/main/resources/application.yaml", "backend/app/src/integrationTest/kotlin/telex/llm/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T6 — Call OpenRouter for text, vision and image through the provider port and classify its errors

## Place in the sequence

- **Blocked by:** T4 — Refresh the catalog from OpenRouter at start, every 24 h and every 5 min after a failure, T5 — Build the in-call fallback loop in llm with one attempt per model, a per-attempt timeout and outcome classification · **Blocks:** T13 — Answer profile slot calls through ProfileCalls and record every call without content · **Wave:** 4 — needs the properties (T4) and the port (T5).
- **Lane:** **compile-coupled pair with T5** (`ModelProvider.kt`); shares the version catalog and `application.yaml` with T4 — serialized.

## Why (user story)

> **As an** Owner
> **I want** teleX to switch to the next model in the chain when a model is gone or fails, and to show me while a profile's main model is missing
> **So that** a vanished or broken model doesn't stop my helpers and a lasting switch doesn't surprise me
>
> — `spec.md §4, US-82, verbatim` · full text: [spec.md](../spec.md)

This task makes the fallback loop talk to the real provider for all three slots and turns provider errors into the outcomes that decide move-on or stop.

## Inlined context

> Spring AI 2.0.1 (BOM already applied through `spring.ai.conventions`). **Added by this feature:** the Spring AI OpenAI-compatible chat model starter, pointed at OpenRouter's endpoint. Versions only in `gradle/libs.versions.toml`.
>
> — `sad.md §2, Technical constraints, abridged` · full text: [sad.md](../sad.md)

> | Image generation through OpenRouter goes over the chat endpoint with image output, which Spring AI's OpenAI chat model may not parse | Medium | The `llm` adapter calls image models with its own `RestClient` request. The QG-5 smoke check proves all three slots with a real key before shipping |
> | OpenRouter's `/models` schema or error bodies change, so capability, price or outcome mapping is wrong | Medium | Parse defensively […]. Map errors by HTTP status first and by the body second. WireMock fixtures are copied from real responses |
>
> — `sad.md §11, Risks, rows 1–2, abridged` · full text: [sad.md](../sad.md)

> Decision drivers: AC-224 / AC-228: classify each failure. Unavailable, provider error, rate limit, timeout and request too large move on. Content refusal and invalid request stop.
>
> — `adr/0003 §Decision drivers, abridged` · full text: [ADR-0003](../adr/0003-client-side-fallback-loop-in-llm.md)

> Data classification internal. teleX never stores or logs request or answer text (spec §6.1). A profile call passes the caller's request only to the model provider and returns the answer to the caller, and the call record is content-free by construction (AC-229). Nothing an Owner does in E10 triggers a model call.
>
> The provider key is an installation secret. It is set only through installation settings (environment), never returned to the browser, never logged (spec §6.1).
>
> — `sad.md §2, Regulatory / external, abridged` · full text: [sad.md](../sad.md)

**Proposed status mapping (not fixed upstream — confirm against OpenRouter's error docs and the copied fixtures, record the final table in the PR):** 429 → `RATE_LIMITED`; 408 / client timeout → `TIMEOUT`; context-length or 413 errors → `TOO_LARGE`; moderation / content-policy refusal → `CONTENT_REFUSED`; other 400 / 422 → `INVALID_REQUEST`; model gone or no provider for it (e.g. 404, 502/503 "no endpoints") → `UNAVAILABLE`; other 5xx → `PROVIDER_ERROR`.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — implements T5's `internal/call/ModelProvider`. Outbound: OpenRouter chat completions (text and vision, through the Spring AI OpenAI-compatible chat model with `base-url` = `telex.llm.openrouter.base-url`) and an image-output request through its own `RestClient`. The answer for an image slot is image bytes + media type (`SlotAnswer`, public-api.md).

— `public-api.md, SlotRequest / SlotAnswer, abridged` · full text: [public-api.md](../contracts/public-api.md)

## Acceptance criteria

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

- [ ] Add the Spring AI OpenAI chat model starter (version from the Spring AI BOM) to `gradle/libs.versions.toml` + `backend/app/build.gradle.kts`; disable its auto-configured defaults that would require `spring.ai.openai.api-key` at startup without a key
- [ ] `OpenRouterChatProvider` (text + vision) and `OpenRouterImageClient` (RestClient) implementing `ModelProvider` — `backend/app/src/main/kotlin/telex/llm/internal/openrouter/`
- [ ] `OpenRouterErrors`: status first, body second → `AttemptOutcome` — `backend/app/src/main/kotlin/telex/llm/internal/openrouter/`
- [ ] WireMock integration tests per slot and per error class, plus the loop end to end (429 → B answers; stall → B answers; refusal → stop) — `backend/app/src/integrationTest/kotlin/telex/llm/`

## Edge cases

| Case | Behaviour |
|---|---|
| App started without a key | Context still boots; the provider is never called (`AI_NOT_CONFIGURED` from T5) |
| Image model answers with no image in the body | `PROVIDER_ERROR`, move on |
| Provider error body unparsable | Classify by HTTP status alone |
| Request or answer text in a log line or exception message | Never — log model id and outcome only |

## Definition of Done

- [ ] the WireMock adapter integration tests pass for text, vision, image and each error class
- [ ] the app boots with and without `TELEX_OPENROUTER_API_KEY`
- [ ] every Hard Rule inlined above still holds
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
