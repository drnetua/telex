---
id: T17
title: "Build the Profiles tab: the profile picker, profile cards, delete and the AI-not-set-up alert"
layer: "ui"
deps: ["T16"]
blocks: ["T18"]
acs: ["AC-10", "AC-51", "AC-210", "AC-218", "AC-219", "AC-220", "AC-223", "AC-226"]
files_hint: ["frontend/src/pages/models/ProfilesTab.tsx", "frontend/src/components/ModelProfilePicker/", "frontend/src/components/ModelProfileCard/", "frontend/src/messages.ts", "frontend/src/api/models.ts"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T17 — Build the Profiles tab: the profile picker, profile cards, delete and the AI-not-set-up alert

## Place in the sequence

- **Blocked by:** T16 — Add the models API hooks, the Models route and link, and the Model catalog tab · **Blocks:** T18 — Build the profile editor modal with the chain editor and the model chooser · **Wave:** 2 — needs the page, hooks and Cost (T16).
- **Lane:** shares `messages.ts` and `api/models.ts` with T16/T18 — serialized.

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

This task is where the Owner compares profiles by price, picks a default, sees fallback warnings and deletes their own profiles.

## Inlined context

> | default | `200`. Picker on top, then the system profile cards (Fast and cheap, Balanced, Careful), then "Your profiles" | `ModelProfilePicker` (C-22, ported) with one radio per profile: name, `Badge` "Default" on the current one, price per 100 runs. "Create profile" `Button` (icon `plus`). One `ModelProfileCard` (NEW) per profile |
> | price states | `estimate` → "≈ $0.30 per 100 runs"; `under-one-cent` → "< $0.01 per 100 runs"; `free` → "Free"; `unknown` → "Price unknown" (still choosable) |
> | choosing | A radio is chosen: `setDefaultModelProfile` sends. That option shows a spinner and the other radios are read-only |
> | chosen | `200` → `Badge` "Default" moves. `Toast` info "<profile> is now your default profile." (AC-51) |
> | choose refused | `409 no-text-model` or `404 not-found`: selection reverts, the list refetches, error `Toast` "<profile> can't be your default right now. Choose another profile." / "This profile no longer exists." |
> | fallback warning | A slot's `state = fallback` (AC-10, AC-227). The card's slot row and the picker option show "Main model unavailable. Using <model> for now." The price follows `currentModelId` |
> | no model available | Slot row: "No model available". On a custom card, a "Pick another model" link → SCR-34 for that profile. On a system card: "Choose another profile" → focuses the picker |
> | no text model | `choosable = false`: the picker option shows "No model available for text", with no price and the radio disabled. If it is the current default, a Tabler `alert` (warning-subtle) above the picker says "Your default profile has no model for text. Choose another profile." |
> | not used | Empty vision or image slot: "Not used" in secondary text |
> | not in the catalog | `availability = not-in-catalog` or `not-capable`: the model id with `Badge` neutral "Not in the catalog" (icon `circle-off`) |
> | system card | `Badge` neutral "System" (icon `lock`), the line "System profiles can't be changed. Duplicate it to make your own.", and only a "Duplicate" `Button`. No Edit or Delete (AC-219) |
> | empty | No custom profiles: `EmptyState kind="first"` "Make your own profile with the models you trust." + action "Create profile" |
> | opening editor | "Create profile" or "Duplicate" → `getModelProfileDraft` (with `?from=`) → SCR-34 opens with the draft. The button is busy meanwhile |
> | limit reached | `409 profile-limit-reached` on the draft (AC-218): the editor doesn't open. Error `Toast` "You can have up to 20 custom profiles. Delete one to make room." |
> | saved | SCR-34 closed after save: the list refetches, `Toast` info "Profile saved" |
> | delete confirm | `ConfirmDialog` (danger) "Delete <profile>?". Body "This can't be undone." When it is the default, it adds "It's your default profile, so Balanced becomes the default." Buttons "Delete profile" / "Cancel" |
> | deleted | `Toast` info "<profile> deleted." or, with `defaultReset`, "<profile> deleted. Balanced is now your default profile." (AC-220) |
> | delete refused | `404 not-found`: the list refetches, error `Toast` "This profile no longer exists." |
> | AI not set up | `aiConfigured = false` (AC-226): a Tabler `alert` (warning-subtle, `alert-triangle`) above the tabs: "AI models aren't set up on this installation yet. Ask the person who runs teleX." System cards show every slot "No model available". "Create profile", "Duplicate" and "Edit" are disabled, while "Delete" stays |
>
> — `screens.md §SCR-66, Profiles tab states, abridged` · full text: [screens.md](../screens.md)

> The picker (C-22) is a standalone component that takes the profile list and the current default, so E09 can embed it in the builder (feature ADR-0001).
>
> — `sad.md §4, strategic choice 1, verbatim` · full text: [sad.md](../sad.md)

> - **Busy button.** As in platform-skeleton: an action that calls the API shows its `Button` `busy` (spinner, label kept) and makes the other controls of that form read-only.
> - **Failure routing** (the shared fetch client, unchanged from E01). `401 unauthenticated` → SCR-01 with the path remembered. `401 session-ended` → SCR-92. No answer within 10 s, any `5xx`, or `403 forbidden` → SCR-93. The `400`, `404` and `409` responses below are handled on the screen.
> - **Feedback.** A single action's result is a `Toast`: `info` for success, `error` for a refused action (it stays until dismissed and says what to do). A condition of the whole page is a Tabler `alert` at the top of the page. A problem with one field shows inline under that field.
> - **Status never by color alone.** Every warning pairs an icon (`alert-triangle` warning, `circle-off` none, `lock` system) with words. No `ai` purple anywhere: nothing on these screens is AI output.
> - **Copy.** All strings go in `frontend/src/messages.ts`, in sentence case, with no emoji and no exclamation marks. `<model>` is the catalog name, or the model id when the model isn't in the catalog. `<profile>` is the profile name, always rendered as plain text (spec §6.1).
> - **Widths.** Each screen is checked at 360 px and 1280 px (spec §6). WCAG 2.2 AA with 0 automated violations.
>
> — `screens.md §Shared conventions, abridged` · full text: [screens.md](../screens.md)

**Reuse:** `Badge`, `Button`, `ConfirmDialog`, `EmptyState`, `LoadState`, `Toast`, `Icon`, `Cost` (T16); port `ModelProfilePicker` (C-22) from `docs/docs/design-system/components/ModelProfilePicker/` adding `value`, `onChange`, per-option `busy`, a disabled option with a reason, price states and the warning line; NEW `ModelProfileCard` (Tabler `card` composing `Badge`, `Cost`, `Button`). Wireframes W-66a–W-66d.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Calls `GET /api/v1/models/profiles` (`ModelProfileList { aiConfigured, defaultProfile, customProfileLimit, items[] }`), `PUT /api/v1/models/default-profile` (`{ profile }` → `200` · `404 not-found` · `409 no-text-model`), `DELETE /api/v1/models/profiles/{profileKey}` (`200 { defaultProfile, defaultReset }` · `404`), `GET /api/v1/models/profile-draft?from=` (`409 profile-limit-reached | ai-not-configured`).

— `contracts/openapi.yaml, operations listModelProfiles, setDefaultModelProfile, deleteModelProfile, getModelProfileDraft, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-10 — error (US-82)

> **Given** a profile whose text slot chain is model A then model B, and model A has left the Model Catalog
> **When** the Owner opens the Models page, and when teleX makes a text call with that profile
> **Then** the profile and its text slot show "Main model unavailable. Using model B for now.", the price estimate reflects model B, the call is answered by model B, and once model A returns to the catalog the warning disappears and model A is used again
>
> — `spec.md §5, AC-10, verbatim` · full text: [spec.md](../spec.md)

### AC-51 — happy (US-13)

> **Given** a signed-in Owner whose default profile is still Balanced (every new Owner starts with it) and who has one custom profile
> **When** the Owner opens the profile picker on the Models page and switches the default to Careful
> **Then** each option (Fast and cheap, Balanced, Careful and the custom profile) shows an estimated price per 100 runs, Careful becomes the default, and the Owner sees a confirmation naming it
>
> — `spec.md §5, AC-51, verbatim` · full text: [spec.md](../spec.md)

### AC-210 — error (US-13)

> **Given** the model currently used by a profile's text slot has no price in the Model Catalog
> **When** the Owner looks at that profile in the picker
> **Then** the profile shows "Price unknown" instead of an estimate, and the Owner can still choose it (a model priced at zero shows "Free", and an estimate below one cent shows "< $0.01 per 100 runs")
>
> — `spec.md §5, AC-210, verbatim` · full text: [spec.md](../spec.md)

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

- [ ] Port `ModelProfilePicker` with the new props and its Vitest — `frontend/src/components/ModelProfilePicker/`
- [ ] NEW `ModelProfileCard` (system and custom variants, slot rows, warnings, actions) with its Vitest — `frontend/src/components/ModelProfileCard/`
- [ ] `ProfilesTab`: picker saves on select, cards, empty state, delete confirm, toasts, AI-not-set-up alert — `frontend/src/pages/models/ProfilesTab.tsx`
- [ ] Strings in `frontend/src/messages.ts`, keyed by problem `code` where an error is shown

## Edge cases

| Case | Behaviour |
|---|---|
| Profile name with `<script>` | Rendered as plain text |
| Choosing while a refetch is in flight | Option busy, other radios read-only; revert on refusal |
| Default profile has no text model | Alert above the picker; the profile stays the default |
| Delete while AI isn't set up | Delete still enabled |
| Phone width | One column, 16 px gutter, picker full width |

## Definition of Done

- [ ] Vitest covers every Profiles-tab state listed above
- [ ] status never by color alone; no `ai` purple
- [ ] every Hard Rule inlined above still holds
- [ ] `pnpm run check` clean; ESLint and `tsc --noEmit` 0 warnings
