---
id: T45
title: "Bring screens.md, sad \u00a77, data-model, events.md, the contract and spec \u00a78 in line with the code"
layer: "docs"
deps: ["T39", "T40", "T41", "T43", "T44"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/screens.md", "docs/features/telegram-link/sad.md", "docs/features/telegram-link/data-model.md", "docs/features/telegram-link/contracts/events.md", "docs/features/telegram-link/contracts/openapi.yaml", "docs/features/telegram-link/spec.md"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 — findings D1, D2, D3, D4, D5"
status: "done"
---

# T45 — Bring screens.md, sad §7, data-model, events.md, the contract and spec §8 in line with the code

## Origin

Follow-up from the independent re-review: [`_review/review-2026-10-04.md`](../_review/review-2026-10-04.md), findings **D1, D2, D3, D4, D5** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** n/a (docs)
- **Blocked by:** T39, T40, T41, T43, T44 · **Blocks:** —

## What to change

- D1 screens.md: rewrite the signed-in frame conventions and the SCR-10/SCR-60 rows against AppShell (Accounts under the Settings registry, the banner as the shell's C-04 condition per T43); fix the PageFrame registry row; close noted gap 3; add 503 `telegram-unavailable` to the SCR-10 and SCR-60 `start-refused` rows. Record the PageFrame → AppShell move as a deviation.
- D2 sad §7: `telex.telegram.sessions.active` tags are `connected|reconnecting|session_lost` counting `linked_account` rows by state (LifecycleMetrics); `telex.chat_sync.duration` is recorded once per completed load (first link and each Sign in again).
- D3 data-model.md:14: `event_publication` does not carry the `telegram` events (in-process only); events.md `TelegramChatsChanged` gains `loadedChatIds`.
- D4 openapi.yaml `TelegramUnavailable`: add the case where Telegram had already authorized — the attempt ends and is logged out; reflect T41's 422 for an unregistered number with no replacement session.
- D5 spec §8: tick Q1 (limit default 3, application.yaml) and Q2 (TDLight spike, spike.md + T1) with their resolutions.

## RED first

Docs-only: no test. Verify every claim against the code at HEAD (cite file:line in the commit body), and that `openapi.yaml` still parses and the contract-validated ITs still pass.

## Definition of Done

Every D1–D5 item matches the code; no stale PageFrame reference remains in screens.md. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
