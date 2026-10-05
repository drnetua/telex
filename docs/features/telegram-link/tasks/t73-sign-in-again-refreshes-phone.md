---
id: T73
title: "Sign in again refreshes the account's masked phone along with its name"
layer: "app"
deps: []
blocks: ["T76"]
acs: ["AC-108", "AC-117", "AC-01"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/internal/account/LinkedAccountRows.kt", "backend/app/src/main/kotlin/telex/messaging/internal/account/LinkCompletion.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingCompletionIT.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (eighth pass) — finding K15"
status: "done"
---

# T73 — Sign in again refreshes the account's masked phone along with its name

## Origin

Follow-up from the eighth-pass review: [`_review/review-2026-10-04-r8.md`](../_review/review-2026-10-04-r8.md), finding **K15** (resolved "Fix now" by the user). Read that row first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-108, AC-117, AC-01
- **Blocked by:** — · **Blocks:** T76

## What to change

- K15: `LinkedAccountRows.swapSession` (`:60-71`) updates `display_name` but not `phone_country_code` / `phone_last_digits`. AC-108 matches an account by its Telegram identity, so a changed number still matches. After that sign-in again, the account keeps showing the old masked phone.
- Fix: `swapSession` takes the `MaskedPhone` too, and `LinkCompletion.signInAgain` passes the one from the `TelegramUser`.

## RED first

Integration (`LinkingCompletionIT`, the AC-117 AC-108 sign-in-again case): the row was seeded with phone `'11'/'11'`. Assert that after signing in again as `9996600108` it holds the fake's country code and last two digits (`08`). Today it still holds `'11'/'11'`.

## Definition of Done

After sign in again, the account row holds the masked phone Telegram reports now. Per-task gate clean (unit + integration + detekt/ktlint). No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
