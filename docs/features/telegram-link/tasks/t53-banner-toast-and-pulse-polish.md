---
id: T53
title: "Banner Toast keeps its node, error Toasts are not nested live regions, a pulse 403 in the shell routes again, pulse tests cover 503/504"
layer: "ui"
deps: []
blocks: ["T55"]
acs: ["AC-122", "AC-117", "AC-111", "AC-114", "app-shell/AC-176"]
files_hint: ["frontend/src/shell/StatusBanner/StatusBanner.tsx", "frontend/src/shell/StatusBanner/StatusBanner.test.tsx", "frontend/src/components/Toast/Toast.tsx", "frontend/src/components/Toast/Toast.test.tsx", "frontend/src/shell/pulse.ts", "frontend/src/shell/pulse.test.tsx", "frontend/src/app/layouts.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (third pass) — findings W1, W2, W4, W6"
status: "done"
---

# T53 — Banner Toast keeps its node, error Toasts are not nested live regions, a pulse 403 in the shell routes again, pulse tests cover 503/504

## Origin

Follow-up from the third-pass review: [`_review/review-2026-10-04-r3.md`](../_review/review-2026-10-04-r3.md), findings **W1, W2, W4, W6** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-122, AC-117, AC-111, AC-114, app-shell/AC-176
- **Blocked by:** — · **Blocks:** T55

## What to change

- W1: `StatusBanner.tsx` renders `notices` as the root's only child when there is no `top`, and as the second child otherwise, so the keyed Toast fragment remounts when `top` appears/disappears and the alert is read twice. Render one stable structure (e.g. the alert div conditionally inside a stable root, `notices` always in the same position). Test: the Toast DOM node is the same object (`toBe`) before and after the refetch that clears the condition (409 already-linked case).
- W2: the B5 fix put the single Toast slot inside the persistent `aria-live=polite` region, so error Toasts (`role=alert`) are nested live regions. Keep info Toasts announced through the persistent polite region and error Toasts through an assertive path that is not inside the polite region (e.g. a second persistent `role=alert` region, or the visual slot outside both regions). Keep the B5 guarantee (info text lands in a region that existed before). Test: an error Toast has no live-region ancestor besides its own / the assertive region, and the info test stays green.
- W4: `pulse.ts:34-37` (commit 13e0f3f) strips the route from every non-auth pulse failure, including a 403 while the shell is mounted; app-shell `screens.md:51` says that inside the shell a 403 goes to SCR-93 with Retry. Strip the route only when `!isShellActive()` (SCR-02), keep it inside the shell. Test both.
- W6: add 504 to the `pulse.test.tsx` failed-pulse matrix and 503 + 504 to the routed SCR-02 test in `layouts.test.tsx`; move the timer/stub/failure-handler cleanup of the new `describe` blocks into `afterEach`.

## RED first

Vitest: the Toast node identity assertion (red today), the error-Toast ancestry assertion (red today), a pulse 403 with the shell active carries the SCR-93 route (red today). The added 503/504 cases may pass immediately — they are coverage additions, say so.

## Definition of Done

A banner refusal Toast is announced once; an error Toast has no live-region ancestor besides its own; inside the shell a pulse 403 goes to SCR-93 as app-shell screens.md says; failed-pulse tests cover 0/502/503/504 and clean up in afterEach. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
