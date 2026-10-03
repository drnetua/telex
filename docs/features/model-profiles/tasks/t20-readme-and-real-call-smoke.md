---
id: T20
title: "Document the AI settings in the README and add the real-call smoke check per slot"
layer: "docs"
deps: ["T9", "T13"]
blocks: []
acs: ["AC-225", "AC-226"]
files_hint: ["README.md", "backend/app/src/integrationTest/kotlin/telex/agents/RealProviderSmokeIT.kt", "docs/docs/03-product-spec.md"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T20 — Document the AI settings in the README and add the real-call smoke check per slot

## Place in the sequence

- **Blocked by:** T9 — Build the three system profiles from settings and validate the Operator's slot overrides, T13 — Answer profile slot calls through ProfileCalls and record every call without content · **Blocks:** — · **Wave:** 7 — needs system profiles (T9) and ProfileCalls (T13).
- **Lane:** own lane (`README.md`, the smoke test).

## Why (user story)

> **As an** Operator
> **I want** to set the installation's provider key, and optionally override the default models behind the three system profiles, in the installation settings
> **So that** every Owner gets working profiles without configuring anything
>
> — `spec.md §4, US-83, verbatim` · full text: [spec.md](../spec.md)

This task tells the Operator exactly what to set, and proves before shipping that each slot really answers through a profile.

## Inlined context

> **New installation settings:** `TELEX_OPENROUTER_API_KEY` → `telex.llm.openrouter.api-key` (required for AI; its absence is a startup WARN, not a failure, AC-226). `telex.llm.openrouter.base-url` defaults to `https://openrouter.ai/api/v1`. `telex.llm.attempt-timeout` defaults to `60s`. `telex.llm.catalog.refresh-interval` defaults to `24h` and `telex.llm.catalog.retry-interval` to `5m`. The system-profile slot overrides go under `telex.models.system-profiles.<fast|balanced|careful>.<text|vision|image>` (a list of up to three model ids). The README lists all of them, in the same section as `TELEX_PUBLIC_URL`.
>
> — `sad.md §7, New installation settings, verbatim` · full text: [sad.md](../sad.md)

> | Real call per slot | text, vision and image each answered by a real provider call through a profile before the epic ships | smoke check with a real key, recorded in the E10 pull request (E10 DoD) |
>
> — `spec.md §6, NFR Real call per slot, verbatim` · full text: [spec.md](../spec.md)

> SCR-66 is a new id. It still needs adding to `docs/docs/03-product-spec.md`, as `ux-flows.md` flags. Owner: PM.
>
> — `screens.md §Noted gaps, item 2, abridged` · full text: [screens.md](../screens.md)

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

No DB changes.

## API contract

Internal — the smoke test calls `ProfileCalls.call(owner, ProfileRef.System(BALANCED), slot, request)` for TEXT, VISION and IMAGE (public-api.md).

## Acceptance criteria

### AC-225 — happy (US-83)

> **Given** an Operator who has set the installation's provider key as the README describes, and has either left the system profiles' models alone or overridden the models of Balanced in the installation settings
> **When** the Operator starts teleX
> **Then** the Model Catalog loads without further action, and every Owner sees the three system profiles: with the models that come with teleX where nothing was overridden, and with exactly the Operator's models for Balanced where it was
>
> — `spec.md §5, AC-225, verbatim` · full text: [spec.md](../spec.md)

### AC-226 — error (US-83)

> **Given** an installation started without a provider key
> **When** an Owner opens the Models page
> **Then** teleX works otherwise (sign-in and everything without AI), the Owner sees a banner saying that AI models aren't set up on this installation yet and to ask the person who runs it, the three system profiles are listed with no model available, custom profiles can't be edited, and the Operator finds a startup warning naming the missing setting in the teleX log
>
> — `spec.md §5, AC-226, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] README "Production settings": the AI settings table (env name, property, default), the override format with an example for Balanced, what the Operator sees without a key — `README.md`
- [ ] `RealProviderSmokeIT` with `@EnabledIfEnvironmentVariable(named = "TELEX_OPENROUTER_API_KEY")`; prints the answering model per slot, never the answer text into logs — `backend/app/src/integrationTest/kotlin/telex/agents/RealProviderSmokeIT.kt`
- [ ] Add SCR-66 Models to the screen inventory in `docs/docs/03-product-spec.md`
- [ ] Record the shipped default models (from T9) in the README

## Edge cases

| Case | Behaviour |
|---|---|
| CI without the key | Smoke test skipped, not failed |
| Real key in the README or test output | Never — only the variable name |
| A slot's shipped model unavailable when the smoke runs | The fallback answers; the test reports `fallback = true` and still passes |

## Definition of Done

- [ ] README section reviewed against `application.yaml` (every key and default matches)
- [ ] the smoke check ran once with a real key; its result is pasted in the E10 PR
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
