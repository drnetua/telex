---
id: T26
title: "Bare not-found page, honest default errors, the AI-not-set-up alert above the tabs, and the remaining manifest and a11y details"
layer: "ui"
deps: ["T25"]
acs: ["AC-222", "AC-51", "AC-226", "AC-223"]
files_hint: ["frontend/src/pages/models/", "frontend/src/api/models.ts", "frontend/src/app/AppRoutes.tsx", "frontend/src/components/ModelProfileCard/", "frontend/src/components/ModelChooser/", "frontend/src/components/FilterBar/", "frontend/src/messages.ts", "frontend/src/styles.css", "e2e/tests/models.spec.ts"]
owner: "Anton Husiev"
estimate: "S"
origin: "review-2026-10-03"
status: "todo"
---

# T26 — Bare not-found page, honest default errors, the AI-not-set-up alert above the tabs, and the remaining manifest and a11y details

## Origin

Follow-up task from the independent review — [review-2026-10-03.md](../_review/review-2026-10-03.md). Each finding below cites `file:line` as of commit `9e411fd`; re-check the lines before editing. The ACs are in [spec.md](../spec.md) §5 (source of truth); contracts in [data-model.md](../data-model.md), [openapi.yaml](../contracts/openapi.yaml), [events.md](../contracts/events.md), [public-api.md](../contracts/public-api.md), [screens.md](../screens.md), [sad.md](../sad.md), [adr/](../adr/).

## Findings to fix

### C2 (stage 1, SCR-91, AC-222) — not-found in the app frame
`ModelsPage.tsx:42` renders `NotFoundPage` inside `PageFrame` (route under `AppLayout`, `AppRoutes.tsx:22-28`); screens.md SCR-91 (:234): "Rendered exactly as the platform-skeleton manifest's SCR-91 — Bare system layout". **Fix:** render it in the bare system frame (as the app-wide `*` route does), identical for missing and foreign ids. **Test:** Vitest asserting the bare layout (no app nav) for both cases.

### F2 (stage 2, AC-51) — misleading toast on choose-default failures
`ProfilesTab.tsx:53-58` turns every non-404 failure into "<profile> can't be your default right now…", including 5xx/403/timeouts already routed to SCR-93. **Fix:** return early for routed failures (`e instanceof ApiFailure && e.route`, as other handlers and `pages/auth/failure.ts` do); show `cantBeDefault` only for `409 no-text-model`. **Test:** Vitest.

### F4 (stage 2, AC-226) — AI-not-set-up alert placement
Manifest: alert "at the top of the page, above the tabs". `ModelsPage.tsx:54` shows it only on the profiles tab; `CatalogTab.tsx:108-114` shows its own copy below the tabs after the catalog loads. **Fix:** render once in ModelsPage above the tabs when `profiles.data?.aiConfigured === false || catalog.data?.state === "not-configured"`; drop the CatalogTab copy. **Test:** Vitest on both tabs.

### F6 (stage 2, screens.md) — manifest details
- `ModelProfileCard.tsx:48`: "No model available" row lacks the `circle-off` icon (manifest: "`circle-off` + words").
- `ProfilesTab.tsx:141-149`: the visible "Default profile" heading (W-66a) is missing; "System profiles" is `visually-hidden` — make it visible per the manifest.
- `ModelChooser.tsx:85-86`: rows lack the price (W-34b "$2.50 / $10.00 per 1M" via `Cost precision`).

### F7 (stage 2, a11y/reuse)
- `FilterBar.tsx:42,46`: hard-coded `id="filter-bar-select"` → `useId()`.
- `ModelsPage.tsx:60-79`: `role="tab"` buttons need `aria-controls`, a `role="tabpanel"` with `aria-labelledby`, and Left/Right arrow keys with roving `tabIndex`.
- `ModelsPage.tsx:80-87`, `ProfilesTab.tsx:178-185`, `ProfileEditorModal.tsx:366-373`: three separate `toast-container`s overlap → one shared toast region.

### From the T25 review (follow-ups on the editor, folded in here)
- `ModelsPage.tsx:23`: the result notice now lives in component state and survives navigation inside the Models page (ModelsPage stays mounted across `/settings/models`, `/profiles/new`, `/profiles/:id`), so a sticky error notice (e.g. the 20-profile limit) stays over the next editor. Fix it together with the shared toast region: a notice belongs to the URL entry it was raised for and goes away on the next navigation (or the Owner dismisses it). Vitest.
- `ProfileEditorModal.tsx:70`: `loaded = draft.data !== undefined && draft.isFetchedAfterMount` — `isFetchedAfterMount` is also true after a *failed* fetch, so a cached old draft + a failing opening refetch (409 `profile-limit-reached` or 5xx) opens the form with the stale suggested name. Count the draft as loaded only when data arrived after mount (e.g. `dataUpdatedAt > errorUpdatedAt` and fetched after mount). Vitest: cached draft, opening refetch returns 409 → back to the list with the limit notice.
- `ProfileEditorModal.test.tsx:761-791`: the F1 tests don't exercise the `!loaded` guards — with `refetchOnWindowFocus: false` a refocus sends no request. Trigger the background refetch explicitly (`queryClient.refetchQueries` / `invalidateQueries(modelProfilesKey)`), assert the second request happened, and that the form and the typed value survive a failing refetch.
- `api/models.ts:150`: Create/Duplicate fetch the draft twice (ProfilesTab's `loadDraft` then the modal's `refetchOnMount: "always"`) and flash the loading state. Accept data fetched just before the modal mounted (e.g. compare `dataUpdatedAt` with the mount time) so the card path makes one request.

All copy in `messages.ts`, tokens only, sentence case. Run `e2e` only if Docker + the e2e stack are practical; otherwise keep `e2e/tests/models.spec.ts` compiling and consistent (tsc/eslint).

## Definition of Done

A missing or foreign profile renders SCR-91 in the bare system layout; a routed failure on choosing a default shows no cantBeDefault toast; the AI-not-set-up alert sits above the tabs on both tabs; the circle-off icon, the visible headings and the chooser row price match screens.md; FilterBar ids are unique, tabs follow the ARIA tabs pattern, and toasts share one region; Vitest (and the e2e spec where it asserts these) cover each.

Test first: write the failing test(s) for each finding, run them, classify the first run (GOOD red / BAD red / false-pass / NON-red) and quote the failing line before production code. Never weaken an existing test except where a finding above says the expectation changes by an owner decision.
