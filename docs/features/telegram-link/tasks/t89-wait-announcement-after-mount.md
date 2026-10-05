---
id: T89
title: "The SCR-02 wait card fills its announcement after the live region is in the page, and leaves it empty until the zone settles"
layer: "ui"
deps: []
blocks: ["T91"]
acs: ["AC-02"]
files_hint: ["frontend/src/pages/connect-telegram/Outcomes.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx", "frontend/src/pages/connect-telegram/Outcomes.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (eleventh pass) — H-W22, T4"
status: "todo"
---

# T89 — The SCR-02 wait card fills its announcement after the live region is in the page, and leaves it empty until the zone settles

## Origin

Follow-up from the eleventh-pass review: [`_review/review-2026-10-05-r3.md`](../_review/review-2026-10-05-r3.md), H-W22, T4 (resolved "Fix now" by the user). Read that row first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-02
- **Blocked by:** — · **Blocks:** T91

## What to change

- H-W22: when `me` is cached (Inbox → wizard), `Outcomes.tsx:38-39` sets the text in the first render, so the `role="status"` region is inserted already filled, which screen readers don't reliably announce. Render the region empty and fill it in an effect after mount, once `me` has settled (saved zone on success, device zone on error).
- T4: assert that the region stays empty until `me` settles.

## RED first

Component (Vitest):
- In the late-`/me` test (`ConnectTelegramPage.test.tsx:955-957`), between the "Too many attempts" heading and `answerMe(...)`, assert `getByRole("status")` is empty.
- With `me` already settled, the region is empty in the first committed render and filled afterwards (e.g. render `WaitState` and check the region's text in a `MutationObserver` or a first-render probe).

## Definition of Done

The role=status region is always inserted empty and filled after mount, once me has settled; tests assert it is empty before me settles and filled after, on both the cached and the late-me paths. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)

## How it was done

- The brief says "fill it in an effect after mount". A `setState` in an effect is refused by ESLint (`react-hooks/set-state-in-effect`), so the `role="status"` span has no React children: an effect writes its `textContent` once `me` has settled, and again with "You can try again now." at 0:00. A ref keeps the start announcement to one.
- The first-commit check is a `useLayoutEffect` probe wrapped around `WaitState` in `Outcomes.test.tsx`. It records the region's text before any passive effect runs.
- The new late-`/me` assertion already passed on the old code, which waited for `me` too. Mutation check: dropping the `settled` guard turns it red (`ConnectTelegramPage.test.tsx:959`, `toBeEmptyDOMElement`).
