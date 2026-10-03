---
id: T11
title: "Serve the catalog view, the profile list with picker data, one profile and a new-profile draft"
layer: "app"
deps: ["T3", "T9", "T10"]
blocks: ["T12", "T13", "T14"]
acs: ["AC-211", "AC-212", "AC-213", "AC-218", "AC-222", "AC-225", "AC-226"]
files_hint: ["backend/app/src/main/kotlin/telex/agents/ModelProfiles.kt", "backend/app/src/main/kotlin/telex/agents/ModelCatalogView.kt", "backend/app/src/main/kotlin/telex/agents/ProfileViews.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/ProfileQueries.kt", "backend/app/src/integrationTest/kotlin/telex/agents/profile/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T11 — Serve the catalog view, the profile list with picker data, one profile and a new-profile draft

## Place in the sequence

- **Blocked by:** T3 — Store the catalog snapshot in Postgres, load it at start and hold it in memory, T9 — Build the three system profiles from settings and validate the Operator's slot overrides, T10 — Persist custom profiles, their chains and the default profile, always scoped by Owner · **Blocks:** T12 — Create, update and delete custom profiles and choose the default, each in one transaction, T13 — Answer profile slot calls through ProfileCalls and record every call without content, T14 — Expose the read endpoints: catalog, profile list, one profile and the draft · **Wave:** 5 — needs the snapshot (T3), system profiles (T9) and the repositories (T10).
- **Lane:** shares `telex/agents/ModelProfiles.kt` with T12 — serialized (T12 depends on this task).

## Why (user story)

> **As an** Owner
> **I want** to compare the system profiles and my own profiles by estimated price and pick one as my default
> **So that** my AI helpers start on a model choice whose cost I understand
>
> — `spec.md §4, US-13, verbatim` · full text: [spec.md](../spec.md)

> **As an** Owner
> **I want** to see the models teleX can use, with what each one can do and what it costs
> **So that** I can tell which models suit text, photos or images before I build a profile
>
> — `spec.md §4, US-80, verbatim` · full text: [spec.md](../spec.md)

> **As an** Owner
> **I want** to create, duplicate, edit and delete my own Model Profiles, with a Fallback Chain in each slot
> **So that** my helpers use the models I trust, with backups I chose
>
> — `spec.md §4, US-81, verbatim` · full text: [spec.md](../spec.md)

This task assembles everything the Models page reads — catalog, profiles, slot warnings, prices and the editor's starting draft — in `agents`' own types so `llm` types never reach `web`.

## Inlined context

> Module boundaries: `llm` is an integration ACL that may depend on `shared` only. `web` may depend on core modules (incl. `agents`) but not on `llm`. `agents` may depend on `llm` (`package-info.java` of each). This feature keeps all of them unchanged ([feature ADR-0002](adr/0002-profiles-in-agents-llm-thin-acl.md)).
>
> — `sad.md §2, Technical constraints, Module boundaries, verbatim` · full text: [sad.md](../sad.md)

> `ModelProfiles.kt` # API: list/get/create/duplicate/update/delete, default profile, picker view
> `ModelCatalogView.kt` # API: the catalog for the Models page in agents' own types (llm types never reach web)
>
> — `sad.md §5, Internal decomposition, agents, abridged` · full text: [sad.md](../sad.md)

> S->>D: reads the Owner's custom profiles and default profile by owner → custom profiles with their chains, the default or none (none means Balanced)
> S->>S: builds the system profiles from settings, shipped models or the Operator's overrides
> S->>S: resolves every slot against the current catalog
> S->>S: estimates the price per 100 runs from each text slot's current model
> S-->>UI: catalog with its state and last-updated time, profiles with slot states, prices and the default
>
> — `sad.md §6, Critical flow 3 (open the Models page), steps 3–8, abridged` · full text: [sad.md](../sad.md)

> alt create, or duplicate Balanced: S->>D: counts the Owner's custom profiles and reads their names
>   alt the Owner already has 20 custom profiles: fails with profile limit reached
>   else room left: draft named Balanced copy, or Balanced copy 2 if taken, with models not in the catalog marked
> else edit an own profile: S->>D: reads the profile by owner and id
>   alt not one of the Owner's profiles: fails with profile not found
>
> — `sad.md §6, Critical flow 5, draft + edit branches, abridged` · full text: [sad.md](../sad.md)

> Custom profiles and default profiles are looked up only by `(owner_id, id)`. Another Owner's profile is `404 profile-not-found`, the same as a missing one (AC-222). System profiles are read-only for everyone (`409 system-profile-read-only`, AC-219)
>
> — `sad.md §8, Authorization / tenancy, verbatim` · full text: [sad.md](../sad.md)

**Note:** the contract finalized the not-found code as plain `not-found` (`openapi.yaml` header: "`not-found` reused for AC-222") — use `not-found`, not `profile-not-found`.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes. Reads `model_profile`, `model_profile_slot_model`, `default_model_profile` through T10.

## API contract

Internal API consumed by T14 (`web`). Shapes mirror the contract:

- `ModelCatalog { state: current|update-failed|not-loaded|not-configured, lastRefreshedAt, lastFailedAt, models[] }`, models ordered by name (`getModelCatalog`).
- `ModelProfileList { aiConfigured, defaultProfile: ProfileRef, customProfileLimit: 20, items[] }` — "The 3 system profiles (fast, balanced, careful), then the Owner's custom profiles by model_profile.created_at" (`listModelProfiles`).
- `ModelProfile { ref, name, slots{text,vision,image}, pricePer100Runs, choosable }` (`getModelProfile`; custom only among the caller's own → not found).
- `ModelProfileDraft { name, duplicatedFrom, slots, pricePer100Runs }` — "Without `from`: an empty profile named "". With `from`: a copy of that system or own profile named "<name> copy", or "<name> copy 2"… with every model of the source copied, including ones that left the catalog. Refused before the editor opens when the Owner already has 20 custom profiles (AC-218) or AI isn't set up (AC-226)." (`getModelProfileDraft`).

— `contracts/openapi.yaml, operations getModelCatalog, listModelProfiles, getModelProfile, getModelProfileDraft, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-211 — happy (US-80)

> **Given** a signed-in Owner and a loaded Model Catalog
> **When** the Owner opens the catalog on the Models page, searches by name and filters by the vision slot
> **Then** the Owner sees only models that can understand images and answer in text, each with its name, provider, what it accepts and produces, its price in US dollars (per million input and output tokens, or per image for models that create images) and how much text it can take at once, and sees when the catalog was last updated; the catalog lists only models that fit at least one slot (text: takes and produces text; vision: takes images and produces text; image: creates images)
>
> — `spec.md §5, AC-211, verbatim` · full text: [spec.md](../spec.md)

### AC-212 — error (US-80)

> **Given** the Model Catalog loaded earlier, and its latest automatic refresh has failed
> **When** the Owner opens the catalog
> **Then** the Owner sees the last known list with a note that it couldn't be updated and the time it is from, and profiles keep working with that list, also after teleX restarts; on a first start with no list ever loaded, the Owner sees "The model list isn't available yet", every slot shows no model available, and teleX retries every 5 minutes
>
> — `spec.md §5, AC-212, verbatim` · full text: [spec.md](../spec.md)

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

### AC-222 — authorization (US-81)

> **Given** two Owners, where the first has a custom profile "Night shift"
> **When** the second Owner browses their profiles, opens a link to "Night shift" or tries to make it their default
> **Then** the second Owner never sees "Night shift", and the link shows the same "not found" page as a profile that doesn't exist, so the profile's existence isn't revealed
>
> — `spec.md §5, AC-222, verbatim` · full text: [spec.md](../spec.md)

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

- [ ] `ModelCatalogView` (agents types, from `llm.ModelCatalog`, with `slots` derived) — `backend/app/src/main/kotlin/telex/agents/ModelCatalogView.kt`
- [ ] `ProfileViews` value types (`ModelProfileView`, `SlotView`, `ChainModelView`, `PriceView`, `ModelProfileListView`, `DraftView`) — `backend/app/src/main/kotlin/telex/agents/ProfileViews.kt`
- [ ] `ModelProfiles` read methods `list(ownerId)`, `get(ownerId, ProfileRef)`, `draft(ownerId, from: ProfileRef?)` + domain problems `ProfileNotFound`, `ProfileLimitReached`, `AiNotConfigured` (extend `telex.shared.DomainProblem`) — `backend/app/src/main/kotlin/telex/agents/ModelProfiles.kt`, `backend/app/src/main/kotlin/telex/agents/internal/profile/ProfileQueries.kt`
- [ ] `@ApplicationModuleTest` over the read API with catalog fixtures — `backend/app/src/integrationTest/kotlin/telex/agents/profile/`

## Edge cases

| Case | Behaviour |
|---|---|
| Another Owner's id, or a random uuid | `ProfileNotFound` — identical |
| Draft `from` another Owner's profile | `ProfileNotFound` |
| No provider key | `aiConfigured = false`, catalog `not-configured`, system slots `no-model-available`, draft refused `AiNotConfigured` |
| Stored default points at a profile with no text model | Kept as default; the profile shows `choosable = false` (warning is UI, T17) |
| Provider down | Everything served from the in-memory snapshot — no outbound call (NFR Provider outage) |

## Definition of Done

- [ ] the read-side module test passes
- [ ] no `telex.llm` type appears in a public `agents` signature used by `web`
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
