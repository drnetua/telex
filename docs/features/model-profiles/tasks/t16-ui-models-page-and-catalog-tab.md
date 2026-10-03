---
id: T16
title: "Add the models API hooks, the Models route and link, and the Model catalog tab"
layer: "ui"
deps: []
blocks: ["T17"]
acs: ["AC-211", "AC-212", "AC-226"]
files_hint: ["frontend/src/api/models.ts", "frontend/src/app/AppRoutes.tsx", "frontend/src/components/PageFrame/", "frontend/src/pages/models/ModelsPage.tsx", "frontend/src/pages/models/CatalogTab.tsx", "frontend/src/components/Cost/", "frontend/src/components/FilterBar/", "frontend/src/components/DataTable/", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T16 — Add the models API hooks, the Models route and link, and the Model catalog tab

## Place in the sequence

- **Blocked by:** — · **Blocks:** T17 — Build the Profiles tab: the profile picker, profile cards, delete and the AI-not-set-up alert · **Wave:** 1 — builds against the contract with mocked fetch; parallel with the backend.
- **Lane:** shares `frontend/src/messages.ts`, `api/models.ts` and `AppRoutes.tsx` with T17/T18 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to see the models teleX can use, with what each one can do and what it costs
> **So that** I can tell which models suit text, photos or images before I build a profile
>
> — `spec.md §4, US-80, verbatim` · full text: [spec.md](../spec.md)

This task lets the Owner browse the Model Catalog, search it, filter it by slot and see how fresh it is.

## Inlined context

> The Models page is a React Router route, `/settings/models`, so that E06's Settings navigation can link it. […] Server state goes through TanStack Query. […] Until E06 ships, a "Models" link in the E01 signed-in page frame is the entry.
>
> — `sad.md §4, strategic choice 1, UI architecture, abridged` · full text: [sad.md](../sad.md)

> Route `/settings/models`, tab in the URL: `?tab=profiles` (default) | `?tab=catalog`. Tabler `nav-tabs` "Profiles" / "Model catalog". The page asks for the profiles (`listModelProfiles`) and the catalog (`getModelCatalog`) in parallel, and each tab renders from its own query.
>
> | loading | `getModelCatalog` pending | `LoadState` (6 rows) |
> | default | `state = current` (AC-211, AC-225). A small "Updated <relative time>" line (absolute time in the tooltip) above the table. Columns: Model (name + provider), Takes, Produces, Price (input / output per 1M tokens, or per image), Context. Ordered by name, no pagination, no CSV export. Under 600 px the rows become label/value cards | `FilterBar` (C-28, ported: search + one "Slot" filter Any / Text / Vision / Image, state in the URL, "Reset all"), `DataTable` (C-29, ported, without pagination or export), `Cost` |
> | filtered | Search by name and/or a slot filter, in the browser over the loaded list. Vision shows only models that take images and answer in text (AC-211) |
> | filtered empty | `EmptyState kind="none"` "No models match your search." + action "Reset all" |
> | update failed | `state = update-failed` (AC-212): the list as `default`, with a Tabler `alert` (warning-subtle, `alert-triangle`) above it: "The model list couldn't be updated. It's from <date, time>." |
> | not loaded | `state = not-loaded` (AC-212): `EmptyState kind="blocked"` "The model list isn't available yet. teleX tries again every 5 minutes." + action "Check again" (refetch) |
> | not configured | `state = not-configured` (AC-226): the page alert of "AI not set up" (Profiles tab). The table area shows `EmptyState kind="blocked"` "No models without AI set up." + action "Go to profiles" |
>
> — `screens.md §SCR-66, header + Model catalog tab states, abridged` · full text: [screens.md](../screens.md)

> **Money.** Every price goes through `Cost` (C-27): USD to the cent, "< $0.01" below a cent, the exact value in the tooltip. Catalog prices per million tokens can need more precision than cents (e.g. $0.075). They show 2 to 4 significant decimals, with the exact decimal string in the tooltip (a `Cost` `precision` prop). "Free" and "Price unknown" are words, not `Cost`.
> **Signed-in frame.** SCR-66 renders inside `PageFrame` (E01, temporary). Until E06's AppShell and Settings navigation ship, `PageFrame` gets an interim "Models" link that goes to `/settings/models`.
>
> — `screens.md §Shared conventions, Money + Signed-in frame, abridged` · full text: [screens.md](../screens.md)

> - **Busy button.** As in platform-skeleton: an action that calls the API shows its `Button` `busy` (spinner, label kept) and makes the other controls of that form read-only.
> - **Failure routing** (the shared fetch client, unchanged from E01). `401 unauthenticated` → SCR-01 with the path remembered. `401 session-ended` → SCR-92. No answer within 10 s, any `5xx`, or `403 forbidden` → SCR-93. The `400`, `404` and `409` responses below are handled on the screen.
> - **Feedback.** A single action's result is a `Toast`: `info` for success, `error` for a refused action (it stays until dismissed and says what to do). A condition of the whole page is a Tabler `alert` at the top of the page. A problem with one field shows inline under that field.
> - **Status never by color alone.** Every warning pairs an icon (`alert-triangle` warning, `circle-off` none, `lock` system) with words. No `ai` purple anywhere: nothing on these screens is AI output.
> - **Copy.** All strings go in `frontend/src/messages.ts`, in sentence case, with no emoji and no exclamation marks. `<model>` is the catalog name, or the model id when the model isn't in the catalog. `<profile>` is the profile name, always rendered as plain text (spec §6.1).
> - **Widths.** Each screen is checked at 360 px and 1280 px (spec §6). WCAG 2.2 AA with 0 automated violations.
>
> — `screens.md §Shared conventions, abridged` · full text: [screens.md](../screens.md)

**Reuse:** existing `Badge`, `Button`, `EmptyState`, `LoadState`, `PageFrame`, `Toast`, `Icon` in `frontend/src/components/`; ports `Cost` (C-27, + `precision`), `FilterBar` (C-28), `DataTable` (C-29) from `docs/docs/design-system/components/<Name>/`. Wireframe W-66e/W-66f in screens.md.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Calls `GET /api/v1/models/catalog` → `ModelCatalog { state, lastRefreshedAt, lastFailedAt, models: CatalogModel[] }`; `CatalogModel { modelId, name, provider, takes[], produces[], slots[], inputPricePerMillionTokens, outputPricePerMillionTokens, pricePerImage, contextLength }` (prices are decimal strings or null). `api/models.ts` also declares the hooks T17/T18 use: `listModelProfiles`, `getModelProfile`, `getModelProfileDraft`, `createModelProfile`, `updateModelProfile`, `deleteModelProfile`, `setDefaultModelProfile`, with background refetches sending `X-Telex-Background: 1`.

— `contracts/openapi.yaml, operation getModelCatalog + schemas ModelCatalog, CatalogModel, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

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

### AC-226 — error (US-83)

> **Given** an installation started without a provider key
> **When** an Owner opens the Models page
> **Then** teleX works otherwise (sign-in and everything without AI), the Owner sees a banner saying that AI models aren't set up on this installation yet and to ask the person who runs it, the three system profiles are listed with no model available, custom profiles can't be edited, and the Operator finds a startup warning naming the missing setting in the teleX log
>
> — `spec.md §5, AC-226, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] TanStack Query hooks + TS types for all seven operations through the existing fetch client — `frontend/src/api/models.ts`
- [ ] Routes `/settings/models`, `/settings/models/profiles/new`, `/settings/models/profiles/:id` (editor routes render the page for now; T18 adds the modal) — `frontend/src/app/AppRoutes.tsx`
- [ ] Interim "Models" link in `frontend/src/components/PageFrame/`
- [ ] Port `Cost` (+ `precision`), `FilterBar`, `DataTable` into `frontend/src/components/` with their Vitest tests
- [ ] `ModelsPage` with URL tabs and `CatalogTab` with every state above; strings in `frontend/src/messages.ts` — `frontend/src/pages/models/`

## Edge cases

| Case | Behaviour |
|---|---|
| Vision filter | Only models whose `slots` include `vision` (take images and answer in text) |
| Image model row | Price "$0.04 per image", Context "—" |
| Price null | "Price unknown" in words |
| Phone width | FilterBar search full width, "Slot" opens a sheet, rows become label/value cards |
| `?tab=` missing or unknown | Profiles tab |

## Definition of Done

- [ ] Vitest covers every catalog-tab state listed above
- [ ] tokens only, no raw hex; status never by color alone
- [ ] every Hard Rule inlined above still holds
- [ ] `pnpm run check` clean; ESLint and `tsc --noEmit` 0 warnings
