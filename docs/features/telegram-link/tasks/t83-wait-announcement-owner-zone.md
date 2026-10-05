---
id: T83
title: "The SCR-02 wait card announces the retry time in the Owner's zone, even when the zone arrives after the card"
layer: "ui"
deps: []
blocks: ["T86"]
acs: ["AC-02"]
files_hint: ["frontend/src/pages/connect-telegram/Outcomes.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (tenth pass) — W18"
status: "done"
---

# T83 — The SCR-02 wait card announces the retry time in the Owner's zone, even when the zone arrives after the card

## Origin

Follow-up from the tenth-pass review: [`_review/review-2026-10-05-r2.md`](../_review/review-2026-10-05-r2.md), W18 (resolved "Fix now" by the user). Read that row first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-02
- **Blocked by:** — · **Blocks:** T86

## What to change

- `/connect-telegram` sits under `OnboardingLayout`, which doesn't load `me`. When `me` isn't cached, `WaitState` first renders with no zone, and `useState` (`Outcomes.tsx:36`) fixes the `role="status"` sentence in the device's zone for good.
- Build the live-region sentence once, but only when the `me` query has settled: on success with the saved zone, on error with the device's zone. Until then the live region stays empty. A status region filled after it is inserted is still announced.

## RED first

Component (Vitest), in `ConnectTelegramPage.test.tsx`:
- The Owner's zone is `Asia/Tokyo`, and `GET /me` answers only after the 429 has shown the card. The `role="status"` text reads "… try again at 12:00, in 2:00." Today it reads the device's time.
- The existing Tokyo test also asserts the `role="status"` text.

## How it was done

`Outcomes.tsx` keeps the sentence in state, empty until `useMe()` is no longer pending, and fills it once in render. The wait card's own `/me` request still routes a 5xx to SCR-93, like a first-load `/me` failure on every other page. That was left as it is on purpose: teleX failing to answer `/me` is a teleX failure, not something the wait card should hide.

## Definition of Done

The wait card's announcement and visible text both give the retry time in the Owner's saved zone, whenever `me` arrives. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
