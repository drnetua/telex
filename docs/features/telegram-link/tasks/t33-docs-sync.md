---
id: T33
title: "Bring sad \u00a76/\u00a77, the api-sync open questions and the screens.md registry in line with the code"
layer: "docs"
deps: ["T30", "T36"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/sad.md", "docs/features/telegram-link/contracts/api-sync-report.md", "docs/features/telegram-link/screens.md"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-03 — findings Q11 (tags part), Q13, Q17"
status: "done"
---

# T33 — Bring sad §6/§7, the api-sync open questions and the screens.md registry in line with the code

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **Q11 (tags part), Q13, Q17** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** n/a (docs)
- **Blocked by:** T30, T36 · **Blocks:** —

## What to change

- sad §7: the linking-outcome metric tags must match what `Linking.kt` emits (`refused_already_linked`, `refused_mismatch`, `signed_in_again`, …) — update the doc to the code (or rename in code if the doc is clearly the better contract; prefer the doc update).
- sad §6 Flows 5–7: add the step-mismatch branch and the Telegram-not-answering branch, and document the 8 s step timeout (`TdlightTelegramSessions.kt`); add the unregistered-number-at-code-step and log-out-after-unfinished-authorization behaviour from T29/T30. Tick the two sequence-gap OQs in `contracts/api-sync-report.md`.
- screens.md: flip the "Registered in design-system" column from `pending` to registered for the components already in `docs/design-system.md`; record the SCR-02 layout outcome of T36.

## RED first

Docs-only task: no RED. Verify by re-reading the code paths cited.

## Definition of Done

No artifact contradicts the code on these points; OQs ticked. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
