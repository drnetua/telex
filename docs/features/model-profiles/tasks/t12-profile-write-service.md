---
id: T12
title: "Create, update and delete custom profiles and choose the default, each in one transaction"
layer: "app"
deps: ["T11"]
blocks: ["T15"]
acs: ["AC-51", "AC-213", "AC-218", "AC-219", "AC-220", "AC-221", "AC-223", "AC-226"]
files_hint: ["backend/app/src/main/kotlin/telex/agents/ModelProfiles.kt", "backend/app/src/main/kotlin/telex/agents/ModelProfileDeleted.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/ProfileCommands.kt", "backend/app/src/integrationTest/kotlin/telex/agents/profile/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T12 — Create, update and delete custom profiles and choose the default, each in one transaction

## Place in the sequence

- **Blocked by:** T11 — Serve the catalog view, the profile list with picker data, one profile and a new-profile draft · **Blocks:** T15 — Expose the write endpoints for profiles and the default profile with their problem codes · **Wave:** 6 — needs the read side (T11).
- **Lane:** shares `telex/agents/ModelProfiles.kt` with T11 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to compare the system profiles and my own profiles by estimated price and pick one as my default
> **So that** my AI helpers start on a model choice whose cost I understand
>
> — `spec.md §4, US-13, verbatim` · full text: [spec.md](../spec.md)

> **As an** Owner
> **I want** to create, duplicate, edit and delete my own Model Profiles, with a Fallback Chain in each slot
> **So that** my helpers use the models I trust, with backups I chose
>
> — `spec.md §4, US-81, verbatim` · full text: [spec.md](../spec.md)

This task performs every change an Owner can make on the Models page and keeps the default profile valid through each one.

## Inlined context

> S->>S: checks name rules, text slot filled, slot capability, at most three models each once
> S->>S: checks that models not in the stored profile or the duplicated source are in the catalog
> alt all rules hold: S->>D: writes the profile and its chains, re-checking the limit and the name in one transaction
> else limit reached or name taken meanwhile: fails with profile limit reached, or name taken
> else system profile, or not one of the Owner's profiles: fails with system profile read only, or profile not found
> else no provider key configured: fails with AI not configured
>
> — `sad.md §6, Critical flow 5, save branches, abridged` · full text: [sad.md](../sad.md)

> S->>D: deletes the profile by owner and id, and clears the default if it pointed there, in one transaction
> S->>S: publishes the profile deleted event for E09 → deleted, the default is Balanced again
> Postcondition: the profile is gone, and the Owner's default is never a deleted profile
>
> — `sad.md §6, Critical flow 6, abridged` · full text: [sad.md](../sad.md)

> S->>S: resolves the profile reference among system profiles and the Owner's own
> alt profile found and its text slot has a model in the catalog: stores the Owner's default profile
> else custom profile of another Owner, or no such profile: fails with profile not found
> else no model available for text: fails with no text model
>
> — `sad.md §6, Critical flow 4, abridged` · full text: [sad.md](../sad.md)

> Kotlin type `telex.agents.ModelProfileDeleted`, `data`: `ownerId`, `profileId`, `wasDefault`. Producer: `agents` `ModelProfiles.delete`, in the delete transaction. Consumers: none in E10.
>
> — `events.md §Event agents.model-profile-deleted.v1, abridged` · full text: [events.md](../contracts/events.md)

> Custom profiles and default profiles are looked up only by `(owner_id, id)`. Another Owner's profile is `404 profile-not-found`, the same as a missing one (AC-222). System profiles are read-only for everyone (`409 system-profile-read-only`, AC-219)
>
> — `sad.md §8, Authorization / tenancy, verbatim` · full text: [sad.md](../sad.md)

**Note:** the contract finalized the not-found code as plain `not-found` (`openapi.yaml` header: "`not-found` reused for AC-222") — use `not-found`, not `profile-not-found`.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Table | Change in this task |
|---|---|
| `model_profile` + `model_profile_slot_model` | insert / update with chain replace / delete, under `pg_advisory_xact_lock` for create (T10) |
| `default_model_profile` | upsert on choose (delete for Balanced); `clearIfCustom` before a profile delete |

— `data-model.md §Entities, agents, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal API consumed by T15. Commands and their problems (contract codes):

- `create(ownerId, name, slots, duplicatedFrom?)` → `ModelProfileView` · `validation-failed` + field codes · `profile-limit-reached` · `ai-not-configured` · `not-found` (unknown `duplicatedFrom`).
- `update(ownerId, ref, name, slots)` → `ModelProfileView` · same + `system-profile-read-only` (system key).
- `delete(ownerId, ref)` → `{ defaultProfile, defaultReset }` · `not-found` · `system-profile-read-only`.
- `setDefault(ownerId, ref)` → the new `ProfileRef` · `not-found` · `no-text-model`.

— `contracts/openapi.yaml, operations createModelProfile, updateModelProfile, deleteModelProfile, setDefaultModelProfile, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-51 — happy (US-13)

> **Given** a signed-in Owner whose default profile is still Balanced (every new Owner starts with it) and who has one custom profile
> **When** the Owner opens the profile picker on the Models page and switches the default to Careful
> **Then** each option (Fast and cheap, Balanced, Careful and the custom profile) shows an estimated price per 100 runs, Careful becomes the default, and the Owner sees a confirmation naming it
>
> — `spec.md §5, AC-51, verbatim` · full text: [spec.md](../spec.md)

### AC-213 — happy (US-81)

> **Given** a signed-in Owner
> **When** the Owner duplicates Balanced (the copy is first named "Balanced copy", then "Balanced copy 2" if that is taken), renames it "Cheap vision", puts two models in the text slot, moves the second one up so it becomes the main model, puts one model in the vision slot, leaves the image slot empty and saves
> **Then** "Cheap vision" appears among the Owner's own profiles with its slots, chains in the chosen order, the image slot shown as "Not used" and the estimated price per 100 runs, and the Owner sees "Profile saved"; models of Balanced that were missing from the Model Catalog at the time are copied too, marked "Not in the catalog"
>
> — `spec.md §5, AC-213, verbatim` · full text: [spec.md](../spec.md)

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

### AC-220 — happy (US-81)

> **Given** an Owner whose default profile is their custom profile "Cheap vision"
> **When** the Owner deletes "Cheap vision" and confirms
> **Then** the profile disappears from their list, the default goes back to Balanced, and the Owner is told that Balanced is now the default
>
> — `spec.md §5, AC-220, verbatim` · full text: [spec.md](../spec.md)

### AC-221 — cross-context (US-81)

> **Given** an Owner who added a model to a custom profile in this edit, and that model has left the Model Catalog since the editor was opened; the profile also still holds an older model that left the catalog earlier
> **When** the Owner saves the profile
> **Then** the profile isn't saved and the Owner is told which newly added model is no longer available, so they can pick another; the older missing model is not a reason to refuse, and it stays in the chain marked "Not in the catalog" so that it is used again if it returns (AC-10)
>
> — `spec.md §5, AC-221, verbatim` · full text: [spec.md](../spec.md)

### AC-223 — error (US-82)

> **Given** a profile with a slot that has no model left in the Model Catalog, or an empty vision or image slot
> **When** the Owner opens the Models page, and when teleX needs that slot for a call
> **Then** the slot shows that no model is available for it, or "Not used" when it is empty (for a custom profile, with a prompt to pick another model; for a system profile, with a suggestion to choose another profile), and the call fails with that plain reason instead of being sent anywhere — never to the model of another slot; when it is the text slot, the picker shows the profile as "No model available for text" with no price, it can't be chosen as a new default, and Owners who already have it as their default keep it and see a warning suggesting another profile
>
> — `spec.md §5, AC-223, verbatim` · full text: [spec.md](../spec.md)

### AC-226 — error (US-83)

> **Given** an installation started without a provider key
> **When** an Owner opens the Models page
> **Then** teleX works otherwise (sign-in and everything without AI), the Owner sees a banner saying that AI models aren't set up on this installation yet and to ask the person who runs it, the three system profiles are listed with no model available, custom profiles can't be edited, and the Operator finds a startup warning naming the missing setting in the teleX log
>
> — `spec.md §5, AC-226, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `ModelProfiles` write methods + `ProfileCommands` using T7's rules, T10's repositories and the injected `Clock` — `backend/app/src/main/kotlin/telex/agents/ModelProfiles.kt`, `backend/app/src/main/kotlin/telex/agents/internal/profile/ProfileCommands.kt`
- [ ] Domain problems `ProfileInvalid(errors)`, `SystemProfileReadOnly`, `NoTextModel` (extend `DomainProblem`) — `backend/app/src/main/kotlin/telex/agents/`
- [ ] `ModelProfileDeleted(ownerId, profileId, wasDefault)` at the `agents` root, published in the delete transaction — `backend/app/src/main/kotlin/telex/agents/ModelProfileDeleted.kt`
- [ ] `@ApplicationModuleTest` incl. `Scenario.stimulate(delete).andWaitForEventOfType(ModelProfileDeleted)` — `backend/app/src/integrationTest/kotlin/telex/agents/profile/`

## Edge cases

| Case | Behaviour |
|---|---|
| Delete the default custom profile | Profile gone, default back to Balanced, `defaultReset = true` (AC-220) |
| Choose a profile with no text model as a new default | `no-text-model`; an Owner who already has it keeps it (AC-223) |
| Choose Balanced | Stored default row removed |
| Create while a parallel create took the 20th place | `profile-limit-reached` from the locked re-count |
| Update re-saves an older missing model | Accepted; only newly added missing models refuse (AC-221) |
| No provider key on create/update | `ai-not-configured`; delete still works (screens: Delete stays enabled) |

## Definition of Done

- [ ] the write-side module test and the event scenario pass
- [ ] the default never points at a deleted profile in any test
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
