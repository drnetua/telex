---
id: T8
title: "Resolve slots against the current catalog and estimate the price per 100 runs"
layer: "domain"
deps: ["T2", "T7"]
blocks: ["T9"]
acs: ["AC-10", "AC-210", "AC-223"]
files_hint: ["backend/app/src/main/kotlin/telex/agents/internal/profile/SlotResolution.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/PriceEstimate.kt", "backend/app/src/test/kotlin/telex/agents/profile/"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T8 — Resolve slots against the current catalog and estimate the price per 100 runs

## Place in the sequence

- **Blocked by:** T2 — Define the llm catalog value types, the slot-fit rule and the OpenRouter model-list parser, T7 — Model the custom profile aggregate, ProfileRef and the profile rules in plain Kotlin · **Blocks:** T9 — Build the three system profiles from settings and validate the Operator's slot overrides · **Wave:** 3 — needs the aggregate (T7).
- **Lane:** own lane (new sibling files in `internal/profile/`).

## Why (user story)

> **As an** Owner
> **I want** to compare the system profiles and my own profiles by estimated price and pick one as my default
> **So that** my AI helpers start on a model choice whose cost I understand
>
> — `spec.md §4, US-13, verbatim` · full text: [spec.md](../spec.md)

> **As an** Owner
> **I want** teleX to switch to the next model in the chain when a model is gone or fails, and to show me while a profile's main model is missing
> **So that** a vanished or broken model doesn't stop my helpers and a lasting switch doesn't surprise me
>
> — `spec.md §4, US-82, verbatim` · full text: [spec.md](../spec.md)

This task computes what the Owner reads before choosing: which model a slot uses right now, whether the main model is missing, and the price per 100 runs.

## Inlined context

> Resilience comes from one rule applied everywhere: **model availability is evaluated against the catalog at the moment of use, never stored on the profile.** A profile keeps the model ids the Owner chose, including ones that left the catalog (marked "Not in the catalog"). Warnings, prices and call routing are computed from the current catalog, so a model that returns is used again with no data change (AC-10, AC-221).
>
> — `sad.md §4, Solution strategy, availability rule, verbatim` · full text: [sad.md](../sad.md)

> | Price estimate | Computed in `agents` from the text slot's current model: 100 × (3,000 input + 500 output tokens) × catalog price, rounded to whole cents. States: `estimate`, `under-one-cent`, `free`, `unknown`, `no-text-model`. Money is in USD as `BigDecimal` and the SPA only formats it | spec §6, AC-210 |
> | Availability evaluation | A model is usable when it is in the current catalog and fits the slot. Evaluated at read and call time, never persisted on the profile | §4 |
>
> — `sad.md §8, Price estimate + Availability evaluation, verbatim` · full text: [sad.md](../sad.md)

> | Price estimate | per 100 runs of a typical run of 3,000 input + 500 output tokens on the text slot's current model, rounded to whole cents; "< $0.01" below one cent; "Free" at a price of zero; "Price unknown" when the catalog has no price | unit test over catalog prices |
>
> — `spec.md §6, NFR Price estimate, verbatim` · full text: [spec.md](../spec.md)

**Worked check (from the contract examples):** `test/text-model-a` at $0.15 in / $0.60 out per 1M → 100 × (3,000 × 0.15 + 500 × 0.60) / 1,000,000 = $0.075 → `"0.08"` (half-up), the `Night shift` example in `getModelProfile`. Compare the **unrounded** value with $0.01 for `under-one-cent` ("an estimate below one cent", AC-210).

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — computes the values the contract carries:

- `Slot.state`: `main-model` (position 1 available) · `fallback` (it isn't, a later one is — "Main model unavailable. Using <currentModel> for now.", AC-10) · `no-model-available` (no model of the chain is available, AC-223) · `not-used` (empty vision or image slot). `Slot.currentModelId` = the first available model.
- `ChainModel.availability`: `available` · `not-in-catalog` · `not-capable` (only in a system profile whose override can't do the slot's job, AC-227; skipped like a missing one).
- `PricePer100Runs.state`: `estimate` (with whole-cent `amount`) · `under-one-cent` · `free` · `unknown` · `no-text-model`. `ModelProfile.choosable` = false when the text slot has no available model.

— `contracts/openapi.yaml, schemas Slot, ChainModel, PricePer100Runs, ModelProfile.choosable, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-10 — error (US-82)

> **Given** a profile whose text slot chain is model A then model B, and model A has left the Model Catalog
> **When** the Owner opens the Models page, and when teleX makes a text call with that profile
> **Then** the profile and its text slot show "Main model unavailable. Using model B for now.", the price estimate reflects model B, the call is answered by model B, and once model A returns to the catalog the warning disappears and model A is used again
>
> — `spec.md §5, AC-10, verbatim` · full text: [spec.md](../spec.md)

### AC-210 — error (US-13)

> **Given** the model currently used by a profile's text slot has no price in the Model Catalog
> **When** the Owner looks at that profile in the picker
> **Then** the profile shows "Price unknown" instead of an estimate, and the Owner can still choose it (a model priced at zero shows "Free", and an estimate below one cent shows "< $0.01 per 100 runs")
>
> — `spec.md §5, AC-210, verbatim` · full text: [spec.md](../spec.md)

### AC-223 — error (US-82)

> **Given** a profile with a slot that has no model left in the Model Catalog, or an empty vision or image slot
> **When** the Owner opens the Models page, and when teleX needs that slot for a call
> **Then** the slot shows that no model is available for it, or "Not used" when it is empty (for a custom profile, with a prompt to pick another model; for a system profile, with a suggestion to choose another profile), and the call fails with that plain reason instead of being sent anywhere — never to the model of another slot; when it is the text slot, the picker shows the profile as "No model available for text" with no price, it can't be chosen as a new default, and Owners who already have it as their default keep it and see a warning suggesting another profile
>
> — `spec.md §5, AC-223, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `SlotResolution`: chain + `ModelCatalog` snapshot → slot state, current model, per-model availability — `backend/app/src/main/kotlin/telex/agents/internal/profile/SlotResolution.kt`
- [ ] `PriceEstimate`: text slot's current model → state + `BigDecimal` amount, half-up to cents — `backend/app/src/main/kotlin/telex/agents/internal/profile/PriceEstimate.kt`
- [ ] Unit tests over hand-built catalogs: each state, the $0.075 → $0.08 edge, $0.004 → under-one-cent, both prices 0 → free, one price null → unknown, fallback to B changes the price — `backend/app/src/test/kotlin/telex/agents/profile/`

## Edge cases

| Case | Behaviour |
|---|---|
| Input price known, output price null | `unknown` ("Price unknown"), still choosable |
| Both prices 0 | `free` |
| Raw estimate $0.004 | `under-one-cent`, `amount = null` |
| Text slot has no available model | `no-text-model`, `choosable = false` |
| Model A returns to the catalog | Next resolution is `main-model` on A again — nothing stored changes (AC-10) |
| Catalog `not-loaded` or `not-configured` | Every slot `no-model-available` (empty snapshot) |

## Definition of Done

- [ ] the resolution and price unit tests pass, including the QG-3 rounding edges
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
