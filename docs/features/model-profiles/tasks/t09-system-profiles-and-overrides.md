---
id: T9
title: "Build the three system profiles from settings and validate the Operator's slot overrides"
layer: "app"
deps: ["T4", "T8"]
blocks: ["T11", "T20"]
acs: ["AC-225", "AC-227"]
files_hint: ["backend/app/src/main/kotlin/telex/agents/internal/profile/SystemProfiles.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/SystemProfileProperties.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/OverrideValidator.kt", "backend/app/src/main/resources/application.yaml", "backend/app/src/test/kotlin/telex/agents/profile/", "backend/app/src/integrationTest/kotlin/telex/agents/"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T9 — Build the three system profiles from settings and validate the Operator's slot overrides

## Place in the sequence

- **Blocked by:** T4 — Refresh the catalog from OpenRouter at start, every 24 h and every 5 min after a failure, T8 — Resolve slots against the current catalog and estimate the price per 100 runs · **Blocks:** T11 — Serve the catalog view, the profile list with picker data, one profile and a new-profile draft, T20 — Document the AI settings in the README and add the real-call smoke check per slot · **Wave:** 4 — needs the refresh event (T4) and slot resolution (T8).
- **Lane:** shares `application.yaml` with T4/T6 — serialized.

## Why (user story)

> **As an** Operator
> **I want** to set the installation's provider key, and optionally override the default models behind the three system profiles, in the installation settings
> **So that** every Owner gets working profiles without configuring anything
>
> — `spec.md §4, US-83, verbatim` · full text: [spec.md](../spec.md)

This task gives every Owner three working profiles with no setup, and lets the Operator change them safely through the installation settings.

## Inlined context

> **Chosen:** option 1. The settings are the only source of truth for system profiles, so there is no startup sync that can drift. Profile tables hold only Owner-owned rows with `owner_id NOT NULL`, which keeps the AC-222 filter uniform. A new Owner needs no seeding: no stored default means Balanced.
>
> — `adr/0005 §Decision outcome, verbatim` · full text: [ADR-0005](../adr/0005-system-profiles-in-configuration-by-key.md)

> The system-profile slot overrides go under `telex.models.system-profiles.<fast|balanced|careful>.<text|vision|image>` (a list of up to three model ids). The README lists all of them, in the same section as `TELEX_PUBLIC_URL`.
>
> — `sad.md §7, New installation settings, abridged` · full text: [sad.md](../sad.md)

> **Consumers:** `agents` system-profile validator. It re-validates the Operator's slot overrides against the new catalog and logs one WARN per bad model, naming the profile, slot and model (AC-227). […] The startup validation runs on application start against the loaded snapshot, so a restart without a successful refresh still warns. **Idempotency:** the listener is a pure re-validation (log only).
>
> — `events.md §Event llm.model-catalog-refreshed.v1, abridged` · full text: [events.md](../contracts/events.md)

> Logging: SLF4J. WARN for missing key, failed refresh and invalid overrides (naming profile, slot and model). Never log the provider key, request or answer text, or profile names
>
> — `sad.md §8, Logging, abridged` · full text: [sad.md](../sad.md)

**Gap upstream:** no artifact names the shipped default model ids for Fast and cheap, Balanced and Careful. Pick current OpenRouter ids (text, vision, image per profile) when implementing, put them in `application.yaml`, and record the choice in the PR and README (T20). Display names: "Fast and cheap", "Balanced", "Careful" (spec §1 deviation).

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes — system profiles are configuration only (ADR-0005).

## API contract

Internal — `SystemProfiles` gives T11/T13 the three profiles as chains per slot. Listens to `telex.llm.ModelCatalogRefreshed` with `@ApplicationModuleListener`.

## Acceptance criteria

### AC-225 — happy (US-83)

> **Given** an Operator who has set the installation's provider key as the README describes, and has either left the system profiles' models alone or overridden the models of Balanced in the installation settings
> **When** the Operator starts teleX
> **Then** the Model Catalog loads without further action, and every Owner sees the three system profiles: with the models that come with teleX where nothing was overridden, and with exactly the Operator's models for Balanced where it was
>
> — `spec.md §5, AC-225, verbatim` · full text: [spec.md](../spec.md)

### AC-227 — error (US-83)

> **Given** an Operator who put a model that isn't in the Model Catalog, or one that can't do the slot's job, or more than three models, into a slot of a system profile (an override replaces that slot only; slots the Operator doesn't set keep the models that come with teleX)
> **When** teleX starts or refreshes the catalog
> **Then** teleX keeps running, the Operator finds a warning in the teleX log naming the profile, slot and model, that model is skipped as if it were missing (models beyond the third are ignored), and Owners see the fallback warning from AC-10 on that profile
>
> — `spec.md §5, AC-227, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `SystemProfileProperties` (`telex.models.system-profiles.*`), shipped defaults in `application.yaml`; an override replaces that slot only — `backend/app/src/main/kotlin/telex/agents/internal/profile/`
- [ ] `SystemProfiles`: key → display name + chains (first three ids kept, beyond ignored) — `backend/app/src/main/kotlin/telex/agents/internal/profile/SystemProfiles.kt`
- [ ] `OverrideValidator`: on `ApplicationReadyEvent` and on `ModelCatalogRefreshed`, WARN per model not in the catalog, unfit for the slot, or beyond the third — `backend/app/src/main/kotlin/telex/agents/internal/profile/OverrideValidator.kt`
- [ ] Unit test of the merge; `@ApplicationModuleTest` scenario publishing `ModelCatalogRefreshed` and asserting the WARN lines (log capture) — `backend/app/src/test/kotlin/telex/agents/profile/`, `backend/app/src/integrationTest/kotlin/telex/agents/`

## Edge cases

| Case | Behaviour |
|---|---|
| Override sets Balanced text only | Balanced vision/image and the other profiles keep the shipped models |
| Override lists four models | First three used, the fourth ignored and warned about |
| Override model can't do the slot's job | Kept in the chain as `not-capable`, skipped at call time, WARN (AC-227) |
| Catalog not loaded yet at startup | Every override model warns as missing; the next successful refresh re-validates |
| Unknown profile key or slot under `telex.models.system-profiles` | Startup WARN and ignored — teleX keeps running |

## Definition of Done

- [ ] the merge unit test and the validation scenario pass
- [ ] the app boots with an invalid override and logs the WARN
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
