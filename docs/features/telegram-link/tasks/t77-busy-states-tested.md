---
id: T77
title: "The SCR-10, SCR-60 and SCR-02 busy states each have a test of their label"
layer: "ui"
deps: []
blocks: ["T82"]
acs: ["AC-01", "AC-111", "AC-02"]
files_hint: ["frontend/src/pages/inbox/InboxPage.test.tsx", "frontend/src/pages/accounts/AccountsPage.test.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (ninth pass) — S3"
status: "todo"
---

# T77 — The SCR-10, SCR-60 and SCR-02 busy states each have a test of their label

## Origin

Follow-up from the ninth-pass review: [`_review/review-2026-10-05.md`](../_review/review-2026-10-05.md), S3 (resolved "Fix now" by the user). Read those rows first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-01, AC-111, AC-02
- **Blocked by:** — · **Blocks:** T82

## What to change

- Five `screens.md` busy states are built but untested: SCR-10 `starting` (`InboxPage.tsx:51-52`), SCR-60 `starting` (`AccountsPage.tsx:116-141`) and `unlinking` (`:167-168`), SCR-02 `resending` (`CodeStep.tsx:105-111`) and `starting-again` (`Outcomes.tsx:63-65,172-174`).
- In each page test, keep the POST/DELETE pending (a promise the test resolves) and assert the busy label ("Starting", "Sending a new code", "Unlinking"), `aria-busy="true"` on that button, and that the sibling buttons named in `screens.md` are disabled. Then resolve and check the state that follows.

## RED first

Component (Vitest). These are tests of existing behaviour, so the first run may be green. Prove each one bites by breaking the busy label or the `busy` prop in the page, seeing the test fail, and restoring it.

## Definition of Done

A test holds each request open and finds the busy label, aria-busy and the disabled sibling buttons for SCR-10 starting, SCR-60 starting and unlinking, and SCR-02 resending and starting-again. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
