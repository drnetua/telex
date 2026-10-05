---
id: T38
title: "End-to-end: complete Sign in again, Reconnecting to Connected, banner goes away, AC-119, password and phone refusals"
layer: "tests"
deps: ["T27", "T29", "T30", "T35", "T36", "T37"]
blocks: []
acs: ["AC-117", "AC-122", "AC-119", "AC-106", "AC-107", "AC-01", "AC-116"]
files_hint: ["e2e/tests/telegram-link.spec.ts", "e2e/support/", "e2e/playwright.config.ts", "backend/app/src/main/kotlin/telex/telegram/internal/fake/FakeTelegram.kt"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-03 — findings S12 (UI part)"
status: "done"
---

# T38 — End-to-end: complete Sign in again, Reconnecting to Connected, banner goes away, AC-119, password and phone refusals

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **S12 (UI part)** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-117, AC-122, AC-119, AC-106, AC-107, AC-01, AC-116
- **Blocked by:** T27, T29, T30, T35, T36, T37 · **Blocks:** —

## What to change

- Fake scenarios reachable from a test phone number: an outage (Reconnecting then Connected) and a terminate-later number whose second (re-sign-in) session survives (see the scenario list at the top of FakeTelegram.kt).
- Playwright at both widths with axe on every state visited: full Sign in again → same account Connected and the banner goes away; Reconnecting → Connected on SCR-60; wrong 2FA password; phone refusals (invalid, unregistered); AC-119 with Telegram linking not set up (a project/config that starts the app without app credentials) shows the Toast and does not open the wizard.
- Make the existing link test wait for "N chats" (synced), not the regex `/Syncing chats|\d+ chats?/` that accepts a stuck sync.

## RED first

Write the new specs first and run them: they should fail on behaviour not yet visible (e.g. a stuck sync) only if an earlier fix regressed; state the first-run classification honestly.

## Definition of Done

The T25 DoD is met in full (sign in again completed) plus the paths above, at 360 px and 1280 px, 0 axe violations. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
