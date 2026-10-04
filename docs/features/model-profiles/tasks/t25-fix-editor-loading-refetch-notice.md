---
id: T25
title: "Show the editor loading state, keep edits across background refetches, and show result notices once"
layer: "ui"
deps: []
acs: ["AC-213", "AC-218", "AC-222"]
files_hint: ["frontend/src/pages/models/ProfileEditorModal.tsx", "frontend/src/pages/models/ProfileEditorModal.test.tsx", "frontend/src/api/models.ts", "frontend/src/pages/models/ModelsPage.tsx", "frontend/src/pages/models/ModelsPage.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
origin: "review-2026-10-03"
status: "todo"
---

# T25 — Show the editor loading state, keep edits across background refetches, and show result notices once

## Origin

Follow-up task from the independent review — [review-2026-10-03.md](../_review/review-2026-10-03.md). Each finding below cites `file:line` as of commit `9e411fd`; re-check the lines before editing. The ACs are in [spec.md](../spec.md) §5 (source of truth); contracts in [data-model.md](../data-model.md), [openapi.yaml](../contracts/openapi.yaml), [events.md](../contracts/events.md), [public-api.md](../contracts/public-api.md), [screens.md](../screens.md), [sad.md](../sad.md), [adr/](../adr/).

## Findings to fix

### C1 (stage 1, screens.md SCR-34 `loading`) — loading state not rendered
`ProfileEditorModal.tsx:89` returns `null` while data is pending, so a deep link or the card's Edit (`ProfilesTab.tsx:111`) shows nothing until the GET returns. screens.md:172: "`LoadState` (3 rows) inside the modal". **Fix:** render the modal shell with `<LoadState state="loading" rows={3} />` while pending. **Test:** Vitest case.

### F1 (stage 2, AC-218/AC-226, SCR-34 `save refused`) — background refetch wipes the editor
`ProfileEditorModal.tsx:68-80` reacts to `source.error` even when `source.data` is loaded; `useModelProfileDraft`/`useModelProfile` (`api/models.ts:126-143`) refetch on window focus (query client defaults, `app/queryClient.ts:13-14`). E.g. 19 profiles, open Create, make one in another tab, return → draft refetch 409 → `<Navigate>` (`:77`) closes the editor, edits lost; a 5xx swaps `EditorForm` for `UnavailablePage` and Retry remounts from `initial`. Re-opening `/profiles/new?from=balanced` from history mounts with the cached draft name before the fresh one arrives → `name-taken`. **Fix:** draft and profile queries: `staleTime: Infinity`, `refetchOnWindowFocus: false` (draft: `refetchOnMount: "always"` and don't render the form from stale cached data); only act on the error while `source.data === undefined`. **Test:** Vitest cases for a failing refetch after load keeping the form and the typed value.

### F3 (stage 2, AC-213/AC-218) — notices replay after reload / Back-Forward
`ProfileEditorModal.tsx:77,255-257` push `{ notice }` into history state; `ModelsPage.tsx:22-23,80-87` shows it whenever `location.state.notice` is set and only remembers dismissal in component state. **Fix:** after reading the notice, clear it: `navigate(location.pathname + location.search, { replace: true, state: null })` (or a one-shot store). **Test:** Vitest: the notice shows once; re-rendering the page at the same entry does not show it again.

Leave other ModelsPage concerns (SCR-91 frame, tabs a11y, AI-not-set-up alert, shared toast region) to T26.

## Definition of Done

SCR-34 loading renders LoadState (3 rows) inside the modal while the draft or profile is pending; a window refocus or a failed background refetch never closes the editor or resets typed edits; a result notice from the editor shows once and does not return after reload or Back/Forward; Vitest covers each.

Test first: write the failing test(s) for each finding, run them, classify the first run (GOOD red / BAD red / false-pass / NON-red) and quote the failing line before production code. Never weaken an existing test except where a finding above says the expectation changes by an owner decision.
