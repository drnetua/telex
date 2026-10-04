---
id: T48
title: "SCR-02 survives a failed pulse, and a failed load shows an inline state with Try again and Back"
layer: "ui"
deps: ["T46"]
blocks: ["T50"]
acs: ["AC-122", "AC-109"]
files_hint: ["frontend/src/shell/pulse.ts", "frontend/src/shell/pulse.test.tsx", "frontend/src/api/client.ts", "frontend/src/app/layouts.tsx", "frontend/src/app/layouts.test.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (second pass) — findings B1, B4"
status: "todo"
---

# T48 — SCR-02 survives a failed pulse, and a failed load shows an inline state with Try again and Back

## Origin

Follow-up from the second-pass review: [`_review/review-2026-10-04-r2.md`](../_review/review-2026-10-04-r2.md), findings **B1, B4** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-122, AC-109
- **Blocked by:** T46 · **Blocks:** T50

## What to change

- B1: on SCR-02 `AppShell` is not mounted so `isShellActive()` is false; `routeFor` tags a pulse 0/502/503/504 as `unavailable` and `fetchPulse` rethrows it with that route, so `createAppQueryClient`'s cache handler sends it to `FailureBoundary` and SCR-93 replaces the wizard on a slow (>2 s `PULSE_TIMEOUT_MS`) pulse, a Wi-Fi drop or a proxy 502. A background pulse must never take over a screen: in `fetchPulse`, rethrow connectivity-status failures (and 5xx) without a route, keeping only `sign-in` / `session-ended`; the banner shows offline/not-responding instead (AC-176 in app-shell).
- B4: after dismissing the SCR-02 load-failure Toast the card is three grey bars with no text, Retry or Back (onboarding has no nav). Render an inline failed state in the card instead — message saying what failed, "Try again" (refetch), ghost "Back" to the origin — and drop the dismissible Toast for this case. Copy in messages.ts, sentence case.

## RED first

Vitest: render `/connect-telegram` under `createAppQueryClient()` + `FailureBoundary` with a pulse fetch rejecting status 0 / 502 / timeout → the wizard step stays and the banner shows not-responding/offline, no SCR-93 (red today). Vitest: attempt load fails → inline message, Try again refetches, Back navigates; no dead end.

## Definition of Done

No pulse failure ever hides the wizard; a failed load always offers a way forward. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
