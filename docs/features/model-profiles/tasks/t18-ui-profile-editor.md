---
id: T18
title: "Build the profile editor modal with the chain editor and the model chooser"
layer: "ui"
deps: ["T17"]
blocks: ["T19"]
acs: ["AC-213", "AC-214", "AC-215", "AC-216", "AC-217", "AC-221", "AC-222"]
files_hint: ["frontend/src/pages/models/ProfileEditorModal.tsx", "frontend/src/components/ChainEditor/", "frontend/src/components/ModelChooser/", "frontend/src/app/AppRoutes.tsx", "frontend/src/messages.ts", "frontend/src/api/models.ts"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T18 — Build the profile editor modal with the chain editor and the model chooser

## Place in the sequence

- **Blocked by:** T17 — Build the Profiles tab: the profile picker, profile cards, delete and the AI-not-set-up alert · **Blocks:** T19 — Add Playwright end-to-end tests for the Models page at 360 px and 1280 px with axe and a 500-model load timing · **Wave:** 3 — needs the Profiles tab that opens it (T17).
- **Lane:** shares `messages.ts`, `api/models.ts` and `AppRoutes.tsx` with T16/T17 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to create, duplicate, edit and delete my own Model Profiles, with a Fallback Chain in each slot
> **So that** my helpers use the models I trust, with backups I chose
>
> — `spec.md §4, US-81, verbatim` · full text: [spec.md](../spec.md)

This task is where the Owner shapes a profile: names it, fills each slot's Fallback Chain in order and is told exactly which rule a save breaks.

## Inlined context

> A Tabler `modal` over SCR-66 (`modal-lg`; full screen below `bp-tablet` via `modal-fullscreen-md-down`). Routes `/settings/models/profiles/new[?from=<key>]` and `/settings/models/profiles/:id`. Closing the modal returns to `/settings/models`. Title: "Create profile" (new or duplicate) or "Edit profile".
>
> | default | a Name field, then three slot sections Text / Vision / Image. Each slot lists its chain in order: position 1 is labelled "Main", then "Backup 1", "Backup 2". Each row has the model name, `Badge` "Not in the catalog" when so, and icon `Button`s "Move up", "Move down", "Remove <model>". Each slot has an "Add model" `Button`. Footer: "Save profile" (primary), "Cancel" |
> | duplicate | Draft from `?from=` (AC-213): name "<source> copy" or "<source> copy N", every source model copied, missing ones with "Not in the catalog" |
> | empty slot | Text shows "Add at least one model." in secondary text (it becomes an error on save, AC-215). Vision and Image show "Not used" |
> | reorder | "Move up" / "Move down" swaps a row with its neighbour. Focus stays on the moved row's button. The new order is announced politely ("Test text model B is now Main") (AC-213) |
> | choosing model | "Add model" → `ModelChooser` (NEW) under that slot: search plus the whole catalog list. Rows that can't do the slot's job are disabled with the reason: "Can't understand images" (vision), "Can't create images" (image), "Doesn't take and produce text" (text) (AC-216). Rows already in the slot are disabled with "Already in this slot" (AC-217). Choosing a row adds it at the end of the chain |
> | slot full | "Add model" is disabled, with "A slot holds at most three models." beside it (AC-217) |
> | validation | `400 validation-failed`. Each `errors[]` item goes under its field, and the first one is focused. `name` → "Name the profile", "Use up to 40 characters", "You already have a profile called <name>", "<name> is a system profile name". `slots.text` → "The text slot needs at least one model.". `slots.<slot>[i]` → `model-left-catalog` "<model> is no longer available. Pick another model." (older missing models stay, only marked). Client-side checks of the same name and text-slot rules run first, with the same words |
> | save refused | `409 profile-limit-reached` or `409 ai-not-configured`: the editor stays open with an error `Toast` naming it |
> | not found | `404 not-found` on `getModelProfile`, on the draft's `from`, or on save (AC-222): the modal doesn't open, SCR-91 renders at that URL. A system key in the `:id` route redirects to `/settings/models` |
> | limit / AI on open | `409` on the draft when opened by URL: redirect to `/settings/models` with the SCR-66 feedback |
> | cancel | "Cancel", Esc or the close button: the modal closes and nothing is saved |
>
> — `screens.md §SCR-34, header + states, abridged` · full text: [screens.md](../screens.md)

> - **Busy button.** As in platform-skeleton: an action that calls the API shows its `Button` `busy` (spinner, label kept) and makes the other controls of that form read-only.
> - **Failure routing** (the shared fetch client, unchanged from E01). `401 unauthenticated` → SCR-01 with the path remembered. `401 session-ended` → SCR-92. No answer within 10 s, any `5xx`, or `403 forbidden` → SCR-93. The `400`, `404` and `409` responses below are handled on the screen.
> - **Feedback.** A single action's result is a `Toast`: `info` for success, `error` for a refused action (it stays until dismissed and says what to do). A condition of the whole page is a Tabler `alert` at the top of the page. A problem with one field shows inline under that field.
> - **Status never by color alone.** Every warning pairs an icon (`alert-triangle` warning, `circle-off` none, `lock` system) with words. No `ai` purple anywhere: nothing on these screens is AI output.
> - **Copy.** All strings go in `frontend/src/messages.ts`, in sentence case, with no emoji and no exclamation marks. `<model>` is the catalog name, or the model id when the model isn't in the catalog. `<profile>` is the profile name, always rendered as plain text (spec §6.1).
> - **Widths.** Each screen is checked at 360 px and 1280 px (spec §6). WCAG 2.2 AA with 0 automated violations.
>
> — `screens.md §Shared conventions, abridged` · full text: [screens.md](../screens.md)

**Reuse:** Tabler `modal`, `form-control`, `is-invalid` + `invalid-feedback`; `Button`, `Badge`, `LoadState`, `Toast`, `Cost` (T16); `NotFoundPage` (SCR-91, platform-skeleton). NEW `ChainEditor` and `ModelChooser` (row and disabled-row styling taken from `ChatPicker` C-30). Wireframes W-34a–W-34c.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Calls `GET /api/v1/models/profile-draft[?from=]`, `GET /api/v1/models/profiles/{profileKey}`, `POST /api/v1/models/profiles` (`{ name, duplicatedFrom, slots }` → `201`), `PUT /api/v1/models/profiles/{profileKey}` (`{ name, slots }` → `200`), and the catalog from T16 for the chooser. `400` `errors[].field` = `name` | `slots.<slot>` | `slots.<slot>[<index>]`; codes `name-required`, `name-too-long`, `name-taken`, `name-reserved`, `text-slot-required`, `model-not-capable`, `slot-full`, `model-duplicate`, `model-left-catalog`.

— `contracts/openapi.yaml, operations getModelProfileDraft, getModelProfile, createModelProfile, updateModelProfile + response ProfileInvalid, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

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

### AC-221 — cross-context (US-81)

> **Given** an Owner who added a model to a custom profile in this edit, and that model has left the Model Catalog since the editor was opened; the profile also still holds an older model that left the catalog earlier
> **When** the Owner saves the profile
> **Then** the profile isn't saved and the Owner is told which newly added model is no longer available, so they can pick another; the older missing model is not a reason to refuse, and it stays in the chain marked "Not in the catalog" so that it is used again if it returns (AC-10)
>
> — `spec.md §5, AC-221, verbatim` · full text: [spec.md](../spec.md)

### AC-222 — authorization (US-81)

> **Given** two Owners, where the first has a custom profile "Night shift"
> **When** the second Owner browses their profiles, opens a link to "Night shift" or tries to make it their default
> **Then** the second Owner never sees "Night shift", and the link shows the same "not found" page as a profile that doesn't exist, so the profile's existence isn't revealed
>
> — `spec.md §5, AC-222, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] NEW `ChainEditor` (Main / Backup labels, move up/down, remove, polite live region) with Vitest — `frontend/src/components/ChainEditor/`
- [ ] NEW `ModelChooser` (search + catalog rows, disabled with reason) with Vitest — `frontend/src/components/ModelChooser/`
- [ ] `ProfileEditorModal` on the two child routes: load draft/profile, client checks, save, map `errors[]` to fields, focus first error — `frontend/src/pages/models/ProfileEditorModal.tsx`, `frontend/src/app/AppRoutes.tsx`
- [ ] Strings in `frontend/src/messages.ts`

## Edge cases

| Case | Behaviour |
|---|---|
| Name only spaces | Client check "Name the profile" before any request |
| Model from the duplicated source missing from the catalog | Kept with "Not in the catalog"; save accepted |
| Newly added model left the catalog before save | `model-left-catalog` under that row; the older missing model stays marked |
| `/settings/models/profiles/balanced` | Redirect to `/settings/models` |
| Another Owner's id in the URL | SCR-91, identical to a missing id |
| Phone width | Full-screen modal, sticky footer |

## Definition of Done

- [ ] Vitest covers every SCR-34 state listed above
- [ ] keyboard only: reorder, add, remove and save reachable; focus never lost on reorder
- [ ] every Hard Rule inlined above still holds
- [ ] `pnpm run check` clean; ESLint and `tsc --noEmit` 0 warnings
