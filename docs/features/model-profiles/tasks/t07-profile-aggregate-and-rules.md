---
id: T7
title: "Model the custom profile aggregate, ProfileRef and the profile rules in plain Kotlin"
layer: "domain"
deps: ["T2"]
blocks: ["T8", "T10"]
acs: ["AC-213", "AC-214", "AC-215", "AC-216", "AC-217", "AC-218", "AC-219", "AC-221"]
files_hint: ["backend/app/src/main/kotlin/telex/agents/ModelProfileId.kt", "backend/app/src/main/kotlin/telex/agents/ProfileRef.kt", "backend/app/src/main/kotlin/telex/agents/ModelSlotKind.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/ModelProfile.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/ProfileRules.kt", "backend/app/src/test/kotlin/telex/agents/profile/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T7 — Model the custom profile aggregate, ProfileRef and the profile rules in plain Kotlin

## Place in the sequence

- **Blocked by:** T2 — Define the llm catalog value types, the slot-fit rule and the OpenRouter model-list parser · **Blocks:** T8 — Resolve slots against the current catalog and estimate the price per 100 runs, T10 — Persist custom profiles, their chains and the default profile, always scoped by Owner · **Wave:** 2 — needs only the llm catalog types (T2) for capability checks; parallel with T3 and T5.
- **Lane:** own lane (`telex/agents/internal/profile/` domain files; T8 adds sibling files).

## Why (user story)

> **As an** Owner
> **I want** to create, duplicate, edit and delete my own Model Profiles, with a Fallback Chain in each slot
> **So that** my helpers use the models I trust, with backups I chose
>
> — `spec.md §4, US-81, verbatim` · full text: [spec.md](../spec.md)

This task encodes every rule that keeps an Owner from saving a profile that couldn't work.

## Inlined context

> `agents` holds the domain rules (slot capability, chain size and uniqueness, name rules, the 20-profile limit, system read-only) in plain Kotlin, tested without Spring.
>
> — `sad.md §5, Building block view, verbatim` · full text: [sad.md](../sad.md)

> **Chosen:** option 1. The settings are the only source of truth for system profiles […]. Profile tables hold only Owner-owned rows with `owner_id NOT NULL` […]. A new Owner needs no seeding: no stored default means Balanced.
>
> — `adr/0005 §Decision outcome, abridged` · full text: [ADR-0005](../adr/0005-system-profiles-in-configuration-by-key.md)

> ```kotlin
> sealed interface ProfileRef {
>     data class System(val key: SystemProfileKey) : ProfileRef   // FAST | BALANCED | CAREFUL ("fast", "balanced", "careful")
>     data class Custom(val id: ModelProfileId) : ProfileRef
> }
> enum class ModelSlotKind { TEXT, VISION, IMAGE }      // "text" | "vision" | "image"
> ```
>
> — `public-api.md, ProfileRef + ModelSlotKind, abridged` · full text: [public-api.md](../contracts/public-api.md)

> **IDs:** app-generated UUIDv7 via `telex.shared.Uuid7.next()`, typed per aggregate as `@JvmInline value class XId(override val value: UUID) : TypedId` (ADR-0003).
>
> — `CLAUDE.md §Layout and code conventions, IDs, verbatim` · full text: [CLAUDE.md](../../../../CLAUDE.md)

> Field codes: `name-required`, `name-too-long`, `name-taken`, `name-reserved` (AC-214), `text-slot-required` (AC-215), `model-not-capable` (AC-216), `slot-full`, `model-duplicate` (AC-217), `model-left-catalog` (AC-221 — a newly added model that isn't in the catalog now). `errors[].field` is `name`, `slots.<slot>` or `slots.<slot>[<index>]`.
>
> — `contracts/openapi.yaml, response ProfileInvalid, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

> every model **not** in `duplicatedFrom` is in the catalog now (models copied from the source may be missing and are kept). […] the "newly added model left the catalog" rule (AC-221) compares with the stored profile, so an older missing model stays and is no reason to refuse.
>
> — `contracts/openapi.yaml, createModelProfile + updateModelProfile descriptions, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

> `name` VARCHAR(40), NOT NULL, `1–40` chars, stored trimmed. Unique per Owner ignoring case. System names are reserved in code. Kotlin trims before saving. Kotlin compares with `lowercase()`, and the unique index is the final word on a race.
>
> — `data-model.md §Entities, model_profile, abridged` · full text: [data-model.md](../data-model.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes. (The aggregate maps to `model_profile` + `model_profile_slot_model`; T10 persists it.)

## API contract

Internal — no API surface. Rule violations are returned as a list of `(field, code)` pairs that T12 raises as one `validation-failed` problem and T15 renders.

## Acceptance criteria

### AC-213 — happy (US-81)

> **Given** a signed-in Owner
> **When** the Owner duplicates Balanced (the copy is first named "Balanced copy", then "Balanced copy 2" if that is taken), renames it "Cheap vision", puts two models in the text slot, moves the second one up so it becomes the main model, puts one model in the vision slot, leaves the image slot empty and saves
> **Then** "Cheap vision" appears among the Owner's own profiles with its slots, chains in the chosen order, the image slot shown as "Not used" and the estimated price per 100 runs, and the Owner sees "Profile saved"; models of Balanced that were missing from the Model Catalog at the time are copied too, marked "Not in the catalog"
>
> — `spec.md §5, AC-213, verbatim` · full text: [spec.md](../spec.md)

### AC-214 — error (US-81)

> **Given** an Owner editing a custom profile
> **When** the Owner saves it with a name that is empty or only spaces, longer than 40 characters after trimming, the same as another of their own profiles ignoring letter case, or the same as a system profile name
> **Then** the profile isn't saved and the Owner sees which rule the name breaks ("Name the profile", "Use up to 40 characters", "You already have a profile called Cheap vision" or "Balanced is a system profile name")
>
> — `spec.md §5, AC-214, verbatim` · full text: [spec.md](../spec.md)

### AC-215 — domain invariant (US-81)

> **Given** an Owner editing a custom profile whose text slot has no models
> **When** the Owner tries to save it
> **Then** the profile isn't saved and the Owner is told that the text slot needs at least one model
>
> — `spec.md §5, AC-215, verbatim` · full text: [spec.md](../spec.md)

### AC-216 — domain invariant (US-81)

> **Given** an Owner filling the vision slot of a custom profile
> **When** the Owner tries to add a model that can only read text
> **Then** the model isn't added and the Owner is told that it can't understand images, so it can't go in the vision slot (the same rule holds for the image slot and models that can't create images, and for the text slot and models that don't both take and produce text)
>
> — `spec.md §5, AC-216, verbatim` · full text: [spec.md](../spec.md)

### AC-217 — domain invariant (US-81)

> **Given** an Owner editing a slot that already holds three models, or a slot that already contains a given model
> **When** the Owner tries to add a fourth model, or the same model a second time
> **Then** the model isn't added and the Owner is told that a slot holds at most three models, each only once
>
> — `spec.md §5, AC-217, verbatim` · full text: [spec.md](../spec.md)

### AC-218 — domain invariant (US-81)

> **Given** an Owner who already has 20 custom profiles
> **When** the Owner tries to create or duplicate another one
> **Then** nothing is created and the Owner is told that the limit is 20 custom profiles and that deleting one makes room
>
> — `spec.md §5, AC-218, verbatim` · full text: [spec.md](../spec.md)

### AC-219 — domain invariant (US-81)

> **Given** a signed-in Owner looking at the Balanced system profile
> **When** the Owner tries to change or delete it
> **Then** the change isn't possible, and the Owner is told that system profiles can't be changed and is offered to duplicate it instead
>
> — `spec.md §5, AC-219, verbatim` · full text: [spec.md](../spec.md)

### AC-221 — cross-context (US-81)

> **Given** an Owner who added a model to a custom profile in this edit, and that model has left the Model Catalog since the editor was opened; the profile also still holds an older model that left the catalog earlier
> **When** the Owner saves the profile
> **Then** the profile isn't saved and the Owner is told which newly added model is no longer available, so they can pick another; the older missing model is not a reason to refuse, and it stays in the chain marked "Not in the catalog" so that it is used again if it returns (AC-10)
>
> — `spec.md §5, AC-221, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `ModelProfileId` (UUIDv7 `TypedId`), `SystemProfileKey`, `ProfileRef`, `ModelSlotKind` at the `agents` root — `backend/app/src/main/kotlin/telex/agents/`
- [ ] `ModelProfile` aggregate (name + three ordered chains) and `ProfileRules`: name (trim, ≤ 40, unique ignoring case among own, not a system name), text slot ≥ 1, capability per slot against the catalog, ≤ 3 each once, newly added models in the catalog (vs stored profile or duplicated source), limit 20 — `backend/app/src/main/kotlin/telex/agents/internal/profile/`
- [ ] `copyName(source, takenNames)`: "<name> copy", then "<name> copy 2", "copy 3"… — `backend/app/src/main/kotlin/telex/agents/internal/profile/`
- [ ] Unit tests, one per rule and per field code — `backend/app/src/test/kotlin/telex/agents/profile/`

## Edge cases

| Case | Behaviour |
|---|---|
| Name "  Cheap vision  " | Trimmed to "Cheap vision" before every rule |
| Name "balanced" (lower case) | `name-reserved` — system names compared ignoring case |
| Exactly 40 characters after trimming | Accepted; 41 → `name-too-long` |
| Several rules broken at once | All reported together, one `errors[]` entry each |
| "Balanced copy" and "Balanced copy 2" taken | Draft name "Balanced copy 3" |
| Copied model missing from the catalog | Kept, not a reason to refuse (AC-213, AC-221) |

## Definition of Done

- [ ] the rule unit tests pass with no Spring context
- [ ] `ModularityTest` green
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
