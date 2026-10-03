---
status: draft            # draft | approved
feature_size: "S"
tool: "code"
updated_at: "2026-10-03"
---

# Screens — model-profiles

> The canonical **screen manifest** — every screen in every state — produced by `screens` (between
> `api` and `tasks`) and read by `tasks` (each `ui` task cites SCR ids + states), `implement`
> (builds the screen to the declared states) and `review` (the built screen must match this).
> Downstream stages reference **only this manifest** — never the raw Figma / `.pen` file.

## Source

- **Tool:** code, from `docs/design-system.md` (`tool: code`). This is the canon's choice, not a degradation.
- **File:** the wireframes are inline below.
- **Component inventory:** the `docs/design-system.md` table, plus the reference components this feature ports from `docs/docs/design-system/components/<Name>/` (cited by README): **ModelProfilePicker (C-22)**, **Cost (C-27)**, **FilterBar (C-28)**, **DataTable (C-29)**. "Tabler: x" means a Tabler 1.6 primitive styled by `tokens.json`, as in the platform-skeleton manifest.
- **Reference mockups** (for humans only): `docs/docs/design-system/components/ModelProfilePicker/preview.html` shows the picker's look: radio rows with "≈ $x.xx per 100 runs" and the fallback warning line. `docs/teleX-screens/Builder.html` shows the same picker inside E09's builder "limits" block, the place it will be embedded later.
- **Sources for state derivation:** `spec.md` §5 (AC-10, AC-51, AC-210…AC-227), the `sad.md` §6 flows 3–6 and their `alt`/`else` branches, the `contracts/openapi.yaml` responses (`ModelCatalog.state`, `Slot.state`, `PricePer100Runs.state`, `choosable`, `aiConfigured`, problem codes), and the `ux-flows.md` inventory.
- **Layout decisions (user, 2026-10-03):**
  1. SCR-66 uses two tabs, kept in the URL.
  2. A system profile card offers only Duplicate, with a standing note.
  3. The model chooser lists the whole catalog, with unfit rows disabled and the reason shown.
  4. The picker saves on select.

## Shared conventions

These apply to every screen below. Each table lists only what differs.

- **Signed-in frame.** SCR-66 renders inside `PageFrame` (E01, temporary). Until E06's AppShell and Settings navigation ship, `PageFrame` gets an interim "Models" link that goes to `/settings/models` (sad §4). E06 replaces the link with its Settings entry.
- **Busy button.** As in platform-skeleton: an action that calls the API shows its `Button` `busy` (spinner, label kept) and makes the other controls of that form read-only.
- **Failure routing** (the shared fetch client, unchanged from E01). Not drawn per screen; each `error` row cites it.
  - `401 unauthenticated` → SCR-01 with the path remembered.
  - `401 session-ended` → SCR-92.
  - No answer within 10 s, any `5xx`, or `403 forbidden` → SCR-93.
  - The `400`, `404` and `409` responses below are handled on the screen.
- **Feedback.** A single action's result is a `Toast`: `info` for success, `error` for a refused action (it stays until dismissed and says what to do). A condition of the whole page is a Tabler `alert` at the top of the page (see §Noted gaps: why not `StatusBanner`). A problem with one field shows inline under that field.
- **Money.** Every price goes through `Cost` (C-27): USD to the cent, "< $0.01" below a cent, the exact value in the tooltip. Catalog prices per million tokens can need more precision than cents (e.g. $0.075). They show 2 to 4 significant decimals, with the exact decimal string in the tooltip (a `Cost` `precision` prop, §New components). "Free" and "Price unknown" are words, not `Cost`.
- **Status never by color alone.** Every warning pairs an icon (`alert-triangle` warning, `circle-off` none, `lock` system) with words. No `ai` purple anywhere: nothing on these screens is AI output.
- **Copy.** All strings go in `frontend/src/messages.ts`, in sentence case, with no emoji and no exclamation marks. `<model>` is the catalog name, or the model id when the model isn't in the catalog. `<profile>` is the profile name, always rendered as plain text (spec §6.1).
- **Widths.** Each screen is checked at 360 px and 1280 px (spec §6). WCAG 2.2 AA with 0 automated violations.

## Screens

### SCR-66 — Models

Route `/settings/models`, tab in the URL: `?tab=profiles` (default) | `?tab=catalog`. Tabler `nav-tabs` "Profiles" / "Model catalog". The page asks for the profiles (`listModelProfiles`) and the catalog (`getModelCatalog`) in parallel, and each tab renders from its own query.

**Profiles tab**

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| loading | `listModelProfiles` pending | `LoadState` (4 rows) in place of the picker and the cards | — |
| default | `200`. Picker on top, then the system profile cards (Fast and cheap, Balanced, Careful), then "Your profiles" (AC-51, AC-225) | `ModelProfilePicker` (C-22, ported) with one radio per profile: name, `Badge` "Default" on the current one, price per 100 runs. "Create profile" `Button` (icon `plus`). One `ModelProfileCard` (NEW) per profile | W-66a |
| price states | `PricePer100Runs.state` per option (AC-51, AC-210): `estimate` → `Cost` estimate "≈ $0.30 per 100 runs"; `under-one-cent` → "< $0.01 per 100 runs"; `free` → "Free"; `unknown` → "Price unknown" (still choosable) | `ModelProfilePicker` option meta line | W-66a |
| choosing | A radio is chosen: `setDefaultModelProfile` sends. That option shows a spinner and the other radios are read-only | `ModelProfilePicker` (busy option) | W-66a |
| chosen | `200` → `Badge` "Default" moves. `Toast` info "<profile> is now your default profile." (AC-51) | `Toast` | — |
| choose refused | `409 no-text-model` or `404 not-found` (a profile changed or deleted meanwhile, flow 4): selection reverts, the list refetches, error `Toast` "<profile> can't be your default right now. Choose another profile." / "This profile no longer exists." | `Toast` error | — |
| fallback warning | A slot's `state = fallback` (AC-10, AC-227). The card's slot row and the picker option show "Main model unavailable. Using <model> for now." The price follows `currentModelId`. A later load without the warning just drops it | `ModelProfileCard` slot row + `ModelProfilePicker` option warning line (`alert-triangle`, warning-text) | W-66b |
| no model available | A slot's `state = no-model-available` (AC-223). Slot row: "No model available". On a custom card, a "Pick another model" link → SCR-34 for that profile. On a system card: "Choose another profile" → focuses the picker | `ModelProfileCard` slot row (`circle-off` + words), Tabler link | W-66b |
| no text model | Text slot `no-model-available`, so `choosable = false` (AC-223). The picker option shows "No model available for text", with no price and the radio disabled. If it is the current default, a Tabler `alert` (warning-subtle) above the picker says "Your default profile has no model for text. Choose another profile." | `ModelProfilePicker` (disabled option), Tabler `alert` | W-66b |
| not used | Empty vision or image slot (AC-213, AC-223): "Not used" in secondary text | `ModelProfileCard` slot row | W-66a |
| not in the catalog | `ChainModel.availability = not-in-catalog` or `not-capable` (AC-213, AC-221, AC-227): the model id with `Badge` neutral "Not in the catalog" (icon `circle-off`) | `Badge` | W-66b |
| system card | Every system profile: `Badge` neutral "System" (icon `lock`), the line "System profiles can't be changed. Duplicate it to make your own.", and only a "Duplicate" `Button`. No Edit or Delete (AC-219, user decision 2) | `ModelProfileCard` (system), `Badge`, `Button` | W-66a |
| empty | The Owner has no custom profiles: under "Your profiles", `EmptyState kind="first"` "Make your own profile with the models you trust." + action "Create profile" | `EmptyState` | W-66a |
| opening editor | "Create profile" or "Duplicate" → `getModelProfileDraft` (with `?from=`) → SCR-34 opens with the draft. The button is busy meanwhile | `Button` busy | — |
| limit reached | `409 profile-limit-reached` on the draft (AC-218): the editor doesn't open. Error `Toast` "You can have up to 20 custom profiles. Delete one to make room." | `Toast` error | — |
| saved | SCR-34 closed after save: the list refetches, the new or changed card shows, `Toast` info "Profile saved" (AC-213) | `Toast` | — |
| delete confirm | "Delete" on a custom card → `ConfirmDialog` (danger) "Delete <profile>?". Body "This can't be undone." When it is the default, it adds "It's your default profile, so Balanced becomes the default." Buttons "Delete profile" / "Cancel" | `ConfirmDialog` | W-66c |
| deleted | `200`: the card goes. `Toast` info "<profile> deleted." or, with `defaultReset`, "<profile> deleted. Balanced is now your default profile." (AC-220) | `Toast` | — |
| delete refused | `404 not-found` (deleted in another tab): the list refetches, error `Toast` "This profile no longer exists." | `Toast` error | — |
| AI not set up | `aiConfigured = false` (AC-226): a Tabler `alert` (warning-subtle, icon `alert-triangle`) at the top of the page, above the tabs: "AI models aren't set up on this installation yet. Ask the person who runs teleX." System cards show every slot "No model available". "Create profile", "Duplicate" and "Edit" are disabled, while "Delete" stays. A `409 ai-not-configured` from a raced draft shows the same text as an error `Toast` | Tabler `alert`, disabled `Button`s | W-66d |
| error | Failure routing | — | — |

**Model catalog tab**

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| loading | `getModelCatalog` pending | `LoadState` (6 rows) | — |
| default | `state = current` (AC-211, AC-225). A small "Updated <relative time>" line (absolute time in the tooltip) above the table. Columns: Model (name + provider), Takes, Produces, Price (input / output per 1M tokens, or per image), Context. Ordered by name, no pagination, no CSV export. Under 600 px the rows become label/value cards (DataTable's own behaviour) | `FilterBar` (C-28, ported: search + one "Slot" filter Any / Text / Vision / Image, state in the URL, "Reset all"), `DataTable` (C-29, ported, without pagination or export), `Cost` | W-66e |
| filtered | Search by name and/or a slot filter, in the browser over the loaded list (sad §6). Vision shows only models that take images and answer in text (AC-211) | `FilterBar`, `DataTable` | W-66e |
| filtered empty | No model matches: `EmptyState kind="none"` "No models match your search." + action "Reset all" | `EmptyState` | — |
| update failed | `state = update-failed` (AC-212): the list as `default`, with a Tabler `alert` (warning-subtle, `alert-triangle`) above it: "The model list couldn't be updated. It's from <date, time>." Profiles keep working | Tabler `alert` | W-66f |
| not loaded | `state = not-loaded` (AC-212): `EmptyState kind="blocked"` "The model list isn't available yet. teleX tries again every 5 minutes." + action "Check again" (refetch) | `EmptyState` | W-66f |
| not configured | `state = not-configured` (AC-226): the page alert of "AI not set up" (Profiles tab). The table area shows `EmptyState kind="blocked"` "No models without AI set up." + action "Go to profiles" (switches the tab) | `EmptyState` | — |
| error | Failure routing | — | — |

```text
W-66a  SCR-66 Profiles tab, default (desktop 1280; phone 360 = one column, 16 px gutter)
PageFrame  [teleX]  Inbox  Models                                   [Sign out]
---------------------------------------------------------------------------------
Models                                                                     h1
[ Profiles ]  Model catalog                                            nav-tabs
Default profile                                                             h3
  ( ) Fast and cheap                          ≈ $0.04 per 100 runs
  (o) Balanced                    [Default]   ≈ $0.30 per 100 runs        C-22
  ( ) Careful                                 ≈ $1.20 per 100 runs
  ( ) Cheap vision                            Free
  ( ) Night shift                             Price unknown
System profiles                                                             h3
+-----------------------------------------------------------------------------+
| Balanced   [lock System]                                  ≈ $0.30 / 100 runs |
| Text     1 Test text model A   2 Test text model B                          |
| Vision   1 Test vision model B                                              |
| Image    Not used                                                           |
| System profiles can't be changed. Duplicate it to make your own.            |
|                                                               [ Duplicate ] |
+-----------------------------------------------------------------------------+
  (Fast and cheap, Careful: same card)
Your profiles                                              [ + Create profile ] h3
+-----------------------------------------------------------------------------+
| Cheap vision                                                          Free  |
| Text     1 Test free model                                                  |
| Vision   1 Test vision model B                                              |
| Image    Not used                                                           |
|                                        [ Edit ] [ Duplicate ] [ Delete ]    |
+-----------------------------------------------------------------------------+
  empty: | Make your own profile with the models you trust.  [ Create profile ] |

W-66b  slot warnings inside ModelProfileCard / picker option
| Text     1 test/text-model-a [circle-off Not in the catalog]                |
|          2 Test text model B                                                |
|          (!) Main model unavailable. Using Test text model B for now.        |
| Vision   1 test/gone-model [circle-off Not in the catalog]                  |
|          (o) No model available.  Pick another model >      (custom card)  |
|          (o) No model available.  Choose another profile >  (system card)  |
picker:  ( ) Careful   (o) No model available for text            (disabled)
alert above picker when that is the default:
| (!) Your default profile has no model for text. Choose another profile.    |

W-66c  delete confirm (ConfirmDialog, danger)
+--------------------------------------------+
| Delete Cheap vision?                       |
| This can't be undone. It's your default    |
| profile, so Balanced becomes the default.  |
|                 [ Cancel ] [Delete profile]|
+--------------------------------------------+

W-66d  AI not set up (above the tabs)
| (!) AI models aren't set up on this installation yet. Ask the person who   |
|     runs teleX.                                                            |
  Create profile / Duplicate / Edit disabled; system slots "No model available"

W-66e  Model catalog tab (desktop; phone: FilterBar search full width, "Slot" opens a sheet,
       DataTable rows become label/value cards)
Models
 Profiles  [ Model catalog ]
[ (search) Search             ] [ Slot: Vision v ]  Reset all            C-28
Updated 3 hours ago
+-----------------------------+--------------+----------+------------------------+---------+
| Model                       | Takes        | Produces | Price                  | Context |
+-----------------------------+--------------+----------+------------------------+---------+
| Test vision model B  · test | Text, images | Text     | $2.50 in · $10.00 out  | 200,000 |
|                             |              |          | per 1M tokens          |         |
+-----------------------------+--------------+----------+------------------------+---------+
 (image models: "Price $0.04 per image", Context "—")

W-66f  catalog notes
| (!) The model list couldn't be updated. It's from 2 Oct, 06:00.           |   update-failed
| The model list isn't available yet. teleX tries again every 5 minutes.    |   not-loaded
|                                               [ Check again ]             |
```

### SCR-34 — Profile editor

A Tabler `modal` over SCR-66 (`modal-lg`; full screen below `bp-tablet` via `modal-fullscreen-md-down`). Routes `/settings/models/profiles/new[?from=<key>]` and `/settings/models/profiles/:id`. Closing the modal returns to `/settings/models`. Title: "Create profile" (new or duplicate) or "Edit profile".

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| loading | Opened by URL: the draft (`getModelProfileDraft`) or the profile (`getModelProfile`) is pending. From SCR-66 the draft has already loaded (SCR-66 `opening editor`) | `LoadState` (3 rows) inside the modal | — |
| default | `200`: a Name field, then three slot sections Text / Vision / Image. Each slot lists its chain in order: position 1 is labelled "Main", then "Backup 1", "Backup 2". Each row has the model name, `Badge` "Not in the catalog" when so, and icon `Button`s "Move up", "Move down", "Remove <model>". Each slot has an "Add model" `Button`. Footer: "Save profile" (primary), "Cancel" | Tabler `modal`, `form-control`, `Button`, `Badge`, `ChainEditor` (NEW) | W-34a |
| duplicate | Draft from `?from=` (AC-213): name "<source> copy" or "<source> copy N", every source model copied, missing ones with "Not in the catalog" | as `default` | W-34a |
| empty slot | A slot with no models: Text shows "Add at least one model." in secondary text (it becomes an error on save, AC-215). Vision and Image show "Not used" | `ChainEditor` | W-34a |
| reorder | "Move up" / "Move down" swaps a row with its neighbour. Focus stays on the moved row's button. The new order is announced politely ("Test text model B is now Main") (AC-213) | `ChainEditor` | W-34a |
| choosing model | "Add model" → `ModelChooser` (NEW) under that slot: search plus the whole catalog list. Fitting rows are selectable. Rows that can't do the slot's job are disabled with the reason: "Can't understand images" (vision), "Can't create images" (image), "Doesn't take and produce text" (text) (AC-216). Rows already in the slot are disabled with "Already in this slot" (AC-217). Choosing a row adds it at the end of the chain | `ModelChooser` (NEW) | W-34b |
| slot full | The slot holds three models: "Add model" is disabled, with "A slot holds at most three models." beside it (AC-217) | `Button` disabled + secondary text | W-34a |
| saving | "Save profile" → `createModelProfile` (with `duplicatedFrom`) or `updateModelProfile`. Busy button, fields read-only | `Button` busy | — |
| validation | `400 validation-failed` (AC-214…AC-217, AC-221). Each `errors[]` item goes under its field, and the first one is focused. `name` → under Name: "Name the profile", "Use up to 40 characters", "You already have a profile called <name>", "<name> is a system profile name". `slots.text` → under the Text slot: "The text slot needs at least one model.". `slots.<slot>[i]` → under that row: `model-left-catalog` "<model> is no longer available. Pick another model." (older missing models stay, only marked), `model-not-capable` / `slot-full` / `model-duplicate` with the chooser's words. Client-side checks of the same name and text-slot rules run first, with the same words | Tabler `is-invalid` + `invalid-feedback` (`alert-circle`) | W-34c |
| save refused | `409 profile-limit-reached` (a parallel create took the last place, AC-218) or `409 ai-not-configured` (AC-226): the editor stays open with an error `Toast` naming it ("You can have up to 20 custom profiles. Delete one to make room." / "AI models aren't set up on this installation yet."). `409 system-profile-read-only` can't come from this UI, because system profiles have no Edit | `Toast` error | — |
| saved | `201`/`200` → the modal closes, and SCR-66 shows `saved` | — | — |
| not found | `404 not-found` on `getModelProfile`, on the draft's `from`, or on save (AC-222): the modal doesn't open, SCR-91 renders at that URL. A system key in the `:id` route (e.g. `/profiles/balanced`) redirects to `/settings/models` | SCR-91 | — |
| limit / AI on open | `409` on the draft when opened by URL: redirect to `/settings/models` with the SCR-66 `limit reached` or `AI not set up` feedback | — | — |
| cancel | "Cancel", Esc or the close button: the modal closes and nothing is saved. A duplicate leaves nothing behind (sad §6 flags) | — | — |
| error | Failure routing | — | — |

```text
W-34a  SCR-34 default (desktop modal-lg; phone full screen, footer sticky)
+---------------------------------------------------------------+
| Create profile                                            [x] |
| Name                                                          |
| [ Cheap vision                                             ]  |
|                                                               |
| Text                                                     h4   |
|  Main      Test text model B             [^] [v] [Remove]     |
|  Backup 1  test/text-model-a [Not in the catalog] [^][v][Rm]  |
|  [ + Add model ]                                              |
| Vision                                                        |
|  Main      Test vision model B           [^] [v] [Remove]     |
|  [ + Add model ]                                              |
| Image                                                         |
|  Not used                                                     |
|  [ + Add model ]                                              |
|  (3 models: [ + Add model ] disabled  "A slot holds at most   |
|   three models.")                                             |
|---------------------------------------------------------------|
|                              [ Cancel ]  [ Save profile ]     |
+---------------------------------------------------------------+

W-34b  ModelChooser under the Vision slot
| [ (search) Find a model                                    ]  |
| Test vision model B · test   $2.50 / $10.00 per 1M  [Already in this slot]  (disabled) |
| Test vision model E · test   $0.15 / $0.60 per 1M                          (selectable) |
| Test text model A · test     $0.15 / $0.60 per 1M  [Can't understand images] (disabled) |
|                                                    [ Close ]  |

W-34c  validation
| Name                                                          |
| [ Balanced                                                 ]  |   is-invalid
| (!) Balanced is a system profile name                         |
| Text                                                          |
| (!) The text slot needs at least one model.                   |
| Image                                                         |
|  Main  Test image model D                                     |
|  (!) Test image model D is no longer available. Pick another  |
|      model.                                                   |
```

### SCR-91 — Page not found (reused from platform-skeleton)

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | `/settings/models/profiles/:id` for a profile that doesn't exist **or** belongs to another Owner: `404 not-found`, identical in both cases (AC-222). Rendered exactly as the platform-skeleton manifest's SCR-91 | Bare system layout, `EmptyState kind="none"`, "Go to Inbox" | platform-skeleton `screens.md` W-91 |
| loading / error / empty | N/A: a static page that requests no data | — | — |

## New components

| Component | Why no existing primitive fits | Registered in design-system |
|---|---|---|
| `ModelProfileCard` | No inventory card shows a profile's three slots, chains, slot states and price. `AssistantCard` (C-16) is an agent with run status, not a model choice. It is a Tabler `card` composing `Badge`, `Cost` and `Button`. E09 reuses it for the warning on assistant cards (feature ADR-0001) | registered |
| `ChainEditor` | An ordered list of at most three models with Move up / Move down / Remove and position labels. No inventory component reorders items (`Chip` removes but doesn't order) | registered |
| `ModelChooser` | Search plus a catalog list with disabled-with-reason rows. `ChatPicker` (C-30) has the same pattern (search, rows disabled with the reason) but is built around chats: folders, a multi-select count and avatars. `ModelChooser` takes its list-row and disabled-row styling from C-30 rather than inventing new ones | registered |
| `Cost` `precision` prop | `Cost` (C-27) rounds to cents. Per-million-token catalog prices need 2–4 significant decimals. This is a prop extension, not a new component | registered |
| Ports: `ModelProfilePicker` (C-22), `Cost` (C-27), `FilterBar` (C-28), `DataTable` (C-29) | Existing reference components, ported into `frontend/src/components/` on first use. The picker gains `value`, `onChange`, a per-option `busy` state, a disabled option with a reason, price states and the warning line. `DataTable` is used without pagination or export | registered |

## Noted gaps

1. **"Banner" for AC-226 is a page `alert`, not `StatusBanner` (C-04).** StatusBanner hasn't been ported yet (it arrives with E06), and it is meant for a condition across all of teleX. "AI models aren't set up" only matters on the Models page in E10. When E06 ships StatusBanner and AI is used elsewhere (E14), this condition can move there. Owner: Frontend.
2. **SCR-66 is a new id.** It still needs adding to `docs/docs/03-product-spec.md`, as `ux-flows.md` flags. Owner: PM.
3. **The model chooser lists the whole catalog** (up to ~500 rows), filtered in the browser. QG-4's p95 ≤ 1 s covers the page load. The chooser needs list virtualisation only if it measures slow at 360 px. `implement` checks this.
4. **No "discard changes?" prompt on Cancel.** No AC asks for one. Nothing is lost beyond the Owner's own edit, and a duplicate leaves nothing behind.
