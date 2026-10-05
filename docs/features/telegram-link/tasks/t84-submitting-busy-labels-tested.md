---
id: T84
title: "The SCR-02 code and password steps' submitting labels each have a test"
layer: "tests"
deps: []
blocks: ["T86"]
acs: ["AC-01", "AC-106"]
files_hint: ["frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (tenth pass) — S4"
status: "done"
---

# T84 — The SCR-02 code and password steps' submitting labels each have a test

## Origin

Follow-up from the tenth-pass review: [`_review/review-2026-10-05-r2.md`](../_review/review-2026-10-05-r2.md), S4 (resolved "Fix now" by the user). Read that row first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-01, AC-106
- **Blocked by:** — · **Blocks:** T86

## What to change

`screens.md:75` (SCR-02 `submitting`) names three busy labels. Only "Sending code" has a test. Add tests for "Checking the code" (`CodeStep.tsx:103`) and "Checking the password" (`PasswordStep.tsx:95`).

## RED first

Tests only: the code exists. Hold POST `/code` and POST `/password` open. Assert the busy label, `aria-busy`, a read-only field and a disabled Cancel. Prove each test by mutation: with the busy label or `busy` flag removed, it goes red.

## Definition of Done

Every SCR-02 `submitting` label has a component test of its label, `aria-busy`, read-only field and disabled Cancel. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
