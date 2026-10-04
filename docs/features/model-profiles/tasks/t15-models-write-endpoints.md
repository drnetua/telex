---
id: T15
title: "Expose the write endpoints for profiles and the default profile with their problem codes"
layer: "ports"
deps: ["T12", "T14"]
blocks: ["T19"]
acs: ["AC-51", "AC-213", "AC-214", "AC-215", "AC-216", "AC-217", "AC-219", "AC-220", "AC-221", "AC-223"]
files_hint: ["backend/app/src/main/kotlin/telex/web/api/ModelsController.kt", "backend/app/src/main/kotlin/telex/web/api/ModelsDtos.kt", "backend/app/src/main/kotlin/telex/web/ProblemHandler.kt", "backend/app/src/integrationTest/kotlin/telex/web/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T15 — Expose the write endpoints for profiles and the default profile with their problem codes

## Place in the sequence

- **Blocked by:** T12 — Create, update and delete custom profiles and choose the default, each in one transaction, T14 — Expose the read endpoints: catalog, profile list, one profile and the draft · **Blocks:** T19 — Add Playwright end-to-end tests for the Models page at 360 px and 1280 px with axe and a 500-model load timing · **Wave:** 7 — needs the write service (T12) and the controller from T14.
- **Lane:** shares `ModelsController.kt` with T14 — serialized.

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

This task puts every Owner change on the wire with the exact refusals the editor and picker show.

## Inlined context

> - Every state-changing request (POST, PUT, DELETE) carries `X-XSRF-TOKEN`; a missing or wrong token answers `403 forbidden`.
> - Errors are RFC 9457 `application/problem+json` with `type = urn:telex:error:<code>`, the `code` extension and, for field errors, `errors[]`. `code` keys `frontend/src/messages.ts`.
>
> — `contracts/openapi.yaml, info.description conventions, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

> **Errors:** RFC 9457 `application/problem+json` with `type = urn:telex:error:<code>`, `code` (a kebab-case DNS-1123 label, e.g. `validation-failed`) and `errors[]`; domain errors extend `telex.shared.DomainProblem`, rendered by `telex.web.ProblemHandler`.
>
> — `CLAUDE.md §Layout and code conventions, Errors, verbatim` · full text: [CLAUDE.md](../../../../CLAUDE.md)

> Custom profiles and default profiles are looked up only by `(owner_id, id)`. Another Owner's profile is `404 profile-not-found`, the same as a missing one (AC-222). System profiles are read-only for everyone (`409 system-profile-read-only`, AC-219)
>
> — `sad.md §8, Authorization / tenancy, verbatim` · full text: [sad.md](../sad.md)

**Note:** the contract finalized the not-found code as plain `not-found` (`openapi.yaml` header: "`not-found` reused for AC-222") — use `not-found`, not `profile-not-found`.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

- `POST /api/v1/models/profiles` body `{ name, duplicatedFrom?: ProfileRef, slots: { text: [ModelId], vision: [...], image: [...] } }` → `201 ModelProfile` + `Location: /api/v1/models/profiles/<id>` · `400 validation-failed` · `403` · `404` · `409 profile-limit-reached | ai-not-configured`.
- `PUT /api/v1/models/profiles/{profileKey}` body `{ name, slots }` → `200 ModelProfile` · `400` · `403` · `404` · `409 system-profile-read-only | profile-limit-reached | ai-not-configured`.
- `DELETE /api/v1/models/profiles/{profileKey}` → `200 { defaultProfile, defaultReset }` · `403` · `404` · `409 system-profile-read-only`.
- `PUT /api/v1/models/default-profile` body `{ profile: ProfileRef }` → `200 { profile }` · `400 validation-failed` (`profile` `required`) · `403` · `404` · `409 no-text-model`.
- `400` field codes: `name-required`, `name-too-long`, `name-taken`, `name-reserved`, `text-slot-required`, `model-not-capable`, `slot-full`, `model-duplicate`, `model-left-catalog`; `errors[].field` = `name` | `slots.<slot>` | `slots.<slot>[<index>]`. A name taken by a parallel save (unique index) also answers `name-taken`.
- New top-level codes: `profile-limit-reached`, `system-profile-read-only`, `no-text-model`, `ai-not-configured`.

— `contracts/openapi.yaml, operations createModelProfile, updateModelProfile, deleteModelProfile, setDefaultModelProfile + responses ProfileInvalid, ProfileWriteRefused, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

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

## Checklist

- [ ] POST/PUT/DELETE handlers + request DTOs with bean validation for shape only (rules stay in `agents`) — `backend/app/src/main/kotlin/telex/web/api/ModelsController.kt`, `backend/app/src/main/kotlin/telex/web/api/ModelsDtos.kt`
- [ ] Map `ProfileInvalid` → `400` with `errors[]`, `SystemProfileReadOnly`/`NoTextModel` → `409`, a unique-index violation on name → `400 name-taken` — `backend/app/src/main/kotlin/telex/web/ProblemHandler.kt` (or the problems' own status)
- [ ] Integration tests: each AC's status + code, XSRF required, Location header, cross-Owner 404 — `backend/app/src/integrationTest/kotlin/telex/web/`

## Edge cases

| Case | Behaviour |
|---|---|
| PUT/DELETE on `balanced` | `409 system-profile-read-only` (AC-219) |
| Missing `X-XSRF-TOKEN` | `403 forbidden` |
| Body missing `slots.text` | `400` — treated as an empty text slot → `text-slot-required` |
| Unknown JSON property | `400 validation-failed` (schemas are `additionalProperties: false`) |
| DELETE another Owner's profile | `404 not-found`, nothing deleted |

## Definition of Done

- [ ] the write-endpoint integration tests pass, one assertion per AC
- [ ] `ModularityTest` green
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
