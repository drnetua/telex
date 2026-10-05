---
id: T94
title: "The SCR-02 wait card announces through a live region that the wizard renders from mount"
layer: "ui"
deps: []
blocks: ["T95"]
acs: ["AC-02"]
files_hint: ["frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx", "frontend/src/pages/connect-telegram/Outcomes.tsx", "frontend/src/pages/connect-telegram/Outcomes.test.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (twelfth pass) — W24"
status: "todo"
---

# T94 — The SCR-02 wait card announces through a live region that the wizard renders from mount

## Origin

Follow-up from the twelfth-pass review: [`_review/review-2026-10-05-r4.md`](../_review/review-2026-10-05-r4.md), W24 (resolved "Fix now" by the user). Read that row first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-02
- **Blocked by:** — · **Blocks:** T95

## What to change

- `Outcomes.tsx:46-55,95` fills a `role="status"` span that is inserted together with the card, so the fill can still land before the browser exposes the empty region. `Toast.tsx:30-37,79` shows the robust pattern: a polite region present before any message.
- Render one polite live region from `ConnectTelegramPage`'s mount and give the wait card a way to set its text (context or a callback prop). The card announces the start sentence once `me` has settled, and "You can try again now." at 0:00. Leaving the card clears it.
- Drop the childless span and its imperative `textContent` write. Keep the visible text `aria-hidden` as today.

## RED first

Component (Vitest): the live region exists and is empty before the 429 (on the phone step); after the card shows with `me` cached it holds the start sentence; with `/me` late it stays empty until `me` settles, then holds the Owner's-zone sentence; at 0:00 it holds "You can try again now.". The existing wait tests keep passing against the new region.

## Definition of Done

A polite live region is in the page from the wizard's mount, before any wait card; the wait card's start sentence (in the Owner's zone, once me has settled) and the 0:00 sentence are written there through React state, with no imperative textContent write. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
