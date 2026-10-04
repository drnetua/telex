---
id: T75
title: "The SCR-02 wait-card focus tests control the clock, and the SCR-10 503 test pins that no connection banner shows"
layer: "ui"
deps: []
blocks: ["T76"]
acs: ["AC-02"]
files_hint: ["frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx", "frontend/src/pages/inbox/InboxPage.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (eighth pass) — findings W14, S2"
status: "done"
---

# T75 — The SCR-02 wait-card focus tests control the clock, and the SCR-10 503 test pins that no connection banner shows

## Origin

Follow-up from the eighth-pass review: [`_review/review-2026-10-04-r8.md`](../_review/review-2026-10-04-r8.md), findings **W14, S2** (resolved "Fix now" by the user).

- **ACs:** AC-02 (SCR-02 `wait`); S2 is the SCR-10 `start-refused` state from screens.md
- **Blocked by:** — · **Blocks:** T76

## What to change

- W14: the two wait-card focus tests (`ConnectTelegramPage.test.tsx:825-853`) depend on the real 1 s interval in `WaitState`. Use fake timers (`shouldAdvanceTime`) and advance past `retryAt` explicitly, so the focus is set before the card ticks, whatever the load.
- S2: the Inbox 503 `telegram-unavailable` case also asserts that "teleX isn't responding." is not shown.

## RED first

Test-only. Check that each strengthened test still fails under the mutation it guards: W14, the guard the focus tests pin; S2, `telegram-unavailable` removed from `SCREEN_HANDLED_503`.

## Definition of Done

Both focus tests pass deterministically, without depending on the real clock. The Inbox case fails if the 503 is treated as a connection failure. `pnpm run check` is clean.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
