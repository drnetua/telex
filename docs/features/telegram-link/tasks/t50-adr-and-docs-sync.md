---
id: T50
title: "Record the shell banner extension and the two live channels in the ADRs, and sync sad, the contract and screens.md"
layer: "docs"
deps: ["T46", "T47", "T48", "T49"]
blocks: []
acs: []
files_hint: ["docs/features/app-shell/adr/0006-extend-the-shell-through-client-section-and-server-condition-registries.md", "docs/features/telegram-link/adr/0005-push-live-state-to-the-spa-as-sse-invalidation-hints.md", "docs/features/app-shell/adr/0002-poll-one-background-pulse-every-3-seconds-for-live-signals.md", "docs/features/telegram-link/sad.md", "docs/features/telegram-link/contracts/openapi.yaml", "docs/features/telegram-link/screens.md", "docs/features/telegram-link/spec.md", "docs/design-system.md"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (second pass) — findings D-a, D-b, D-c"
status: "done"
---

# T50 — Record the shell banner extension and the two live channels in the ADRs, and sync sad, the contract and screens.md

## Origin

Follow-up from the second-pass review: [`_review/review-2026-10-04-r2.md`](../_review/review-2026-10-04-r2.md), findings **D-a, D-b, D-c** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** n/a (docs)
- **Blocked by:** T46, T47, T48, T49 · **Blocks:** —

## What to change

- D-a: amend app-shell ADR-0006 (an "Amendment 2026-10-04 (E02)" section, status stays Accepted): the pulse still decides whether a condition is active; a catalog entry may carry an optional client `useLive` hook for live text/action, a `button` action kind, a `notice` slot and the `inactive` suppression flag (T49) — generic, no epic specifics in the banner code; the app-shell §7 KPI "shell changes needed by later epics" was spent once by E02 on this generic extension. Note it as a telegram-link deviation in spec §1 too.
- D-b: amend telegram-link ADR-0005: E06 shipped the pulse first (app-shell ADR-0002); the split is SSE for `linked-accounts` invalidation hints, the pulse for Status Banner conditions (ADR-0006). Drop the "E06 must agree" consequence, cross-reference ADR-0002, and close the sad §11 risk row.
- D-c: sad Flow 9 tail → pulse → `StatusConditionSource` (`SessionLostConditions`) → shell catalog, list only for name/action; add `SessionLostConditions` to the §5 messaging tree and the §8 Live updates row; Flows 5/6: the resend refusal that ends the attempt and the unregistered-with-no-replacement-session branch, plus T46's race outcome (sad Flow 10); `refused_phone` metric note includes invalid. `openapi.yaml` `LiveHint` text: the banner condition comes from the pulse, only its text from the list. screens.md SCR-02 table: the T48 load-failed state, the resend refusal + T46 ended-with-reason state, the unregistered-no-session and telegram-unavailable-after-authorized exceptions, the full `starting-again` refusal set; banner rows reflect T49.

## RED first

Docs-only: no test. Verify each claim against the code at HEAD (cite file:line in the commit body); if openapi.yaml changes, run the contract-validated ITs (LinkingApiIT, LinkedAccountsApiIT) to prove it still parses and matches.

## Definition of Done

ADRs, sad, contract and screens.md describe the code as built; no open sad §11 risk on the live channel. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
