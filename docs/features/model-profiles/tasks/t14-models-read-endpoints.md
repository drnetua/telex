---
id: T14
title: "Expose the read endpoints: catalog, profile list, one profile and the draft"
layer: "ports"
deps: ["T11"]
blocks: ["T15"]
acs: ["AC-10", "AC-211", "AC-212", "AC-218", "AC-222", "AC-226"]
files_hint: ["backend/app/src/main/kotlin/telex/web/api/ModelsController.kt", "backend/app/src/main/kotlin/telex/web/api/ModelsDtos.kt", "backend/app/src/integrationTest/kotlin/telex/web/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T14 — Expose the read endpoints: catalog, profile list, one profile and the draft

## Place in the sequence

- **Blocked by:** T11 — Serve the catalog view, the profile list with picker data, one profile and a new-profile draft · **Blocks:** T15 — Expose the write endpoints for profiles and the default profile with their problem codes · **Wave:** 6 — needs the read service (T11); parallel with T12 and T13.
- **Lane:** shares `telex/web/api/ModelsController.kt` with T15 — serialized.

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

This task puts the Models page's reads on the wire exactly as the contract describes.

## Inlined context

> - JSON property names are camelCase.
> - SPA background refetches carry `X-Telex-Background: 1` (ADR-0005 of platform-skeleton).
> - Errors are RFC 9457 `application/problem+json` with `type = urn:telex:error:<code>`, the `code` extension and, for field errors, `errors[]`.
> - Model availability, slot states and prices are computed against the catalog at the moment of the request and never stored (sad §4).
> - Money is a decimal string in US dollars, never a JSON number.
> - Nothing on this API calls a model. The provider key is never returned.
>
> — `contracts/openapi.yaml, info.description conventions, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

> | Authentication | Every `/api/models/**` endpoint needs a live Sign-in Session (E01 Spring Security). There is no Operator endpoint: the Operator acts only through installation settings | platform-skeleton SAD §8 |
>
> — `sad.md §8, Authentication, verbatim` · full text: [sad.md](../sad.md)

**Note:** the contract paths are `/api/v1/models/**` — the contract wins over the sad's `/api/models/**`.

> Custom profiles and default profiles are looked up only by `(owner_id, id)`. Another Owner's profile is `404 profile-not-found`, the same as a missing one (AC-222). System profiles are read-only for everyone (`409 system-profile-read-only`, AC-219)
>
> — `sad.md §8, Authorization / tenancy, verbatim` · full text: [sad.md](../sad.md)

**Note:** the contract finalized the not-found code as plain `not-found` (`openapi.yaml` header: "`not-found` reused for AC-222") — use `not-found`, not `profile-not-found`.

> Module boundaries: `llm` is an integration ACL that may depend on `shared` only. `web` may depend on core modules (incl. `agents`) but not on `llm`. `agents` may depend on `llm` (`package-info.java` of each). This feature keeps all of them unchanged ([feature ADR-0002](adr/0002-profiles-in-agents-llm-thin-acl.md)).
>
> — `sad.md §2, Technical constraints, Module boundaries, verbatim` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

- `GET /api/v1/models/catalog` → `200 ModelCatalog` · `401`.
- `GET /api/v1/models/profiles` → `200 ModelProfileList` · `401`.
- `GET /api/v1/models/profiles/{profileKey}` (`fast|balanced|careful` or a uuid) → `200 ModelProfile` · `401` · `404 not-found`.
- `GET /api/v1/models/profile-draft[?from=<profileKey>]` → `200 ModelProfileDraft` · `401` · `404 not-found` · `409 profile-limit-reached | ai-not-configured`.
- `ProfileRef` JSON: `{ "kind": "system", "key": "balanced" }` | `{ "kind": "custom", "id": "<uuid>" }`. Money: `UsdAmount` string, e.g. `"0.150000"`; `PricePer100Runs.amount` whole-cent string `"0.30"`.

— `contracts/openapi.yaml, operations getModelCatalog, listModelProfiles, getModelProfile, getModelProfileDraft, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-10 — error (US-82)

> **Given** a profile whose text slot chain is model A then model B, and model A has left the Model Catalog
> **When** the Owner opens the Models page, and when teleX makes a text call with that profile
> **Then** the profile and its text slot show "Main model unavailable. Using model B for now.", the price estimate reflects model B, the call is answered by model B, and once model A returns to the catalog the warning disappears and model A is used again
>
> — `spec.md §5, AC-10, verbatim` · full text: [spec.md](../spec.md)

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

### AC-226 — error (US-83)

> **Given** an installation started without a provider key
> **When** an Owner opens the Models page
> **Then** teleX works otherwise (sign-in and everything without AI), the Owner sees a banner saying that AI models aren't set up on this installation yet and to ask the person who runs it, the three system profiles are listed with no model available, custom profiles can't be edited, and the Operator finds a startup warning naming the missing setting in the teleX log
>
> — `spec.md §5, AC-226, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `ModelsController` GET handlers + `ProfileKey` path parsing (system key or uuid; anything else → 404) — `backend/app/src/main/kotlin/telex/web/api/ModelsController.kt`
- [ ] DTOs mapping `agents` views to the contract schemas (tagged `ProfileRef`, decimal strings, kebab-case enums) — `backend/app/src/main/kotlin/telex/web/api/ModelsDtos.kt`
- [ ] Render `ProfileNotFound` → `404 not-found`, `ProfileLimitReached` → `409 profile-limit-reached`, `AiNotConfigured` → `409 ai-not-configured` via `ProblemHandler`
- [ ] Integration tests with two signed-in Owners and the examples from the contract — `backend/app/src/integrationTest/kotlin/telex/web/`

## Edge cases

| Case | Behaviour |
|---|---|
| `/profiles/not-a-key` | `404 not-found` (not a 400) — same as any unknown profile |
| Owner B GETs Owner A's profile id | `404 not-found`, body identical to a random uuid |
| No session | `401 unauthenticated` |
| Provider down | `200` from the stored snapshot, `state` reflects the last failure |
| A model in the fallback state | `slots.text.state = fallback`, `currentModelId` = B, price from B (AC-10) |

## Definition of Done

- [ ] the read-endpoint integration tests pass and match the contract examples
- [ ] `ModularityTest` green — `web` imports nothing from `telex.llm`
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
