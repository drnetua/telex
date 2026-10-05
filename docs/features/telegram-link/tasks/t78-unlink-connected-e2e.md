---
id: T78
title: "An e2e test unlinks a connected account and sees the unlinked Toast and Connect Telegram"
layer: "e2e"
deps: []
blocks: ["T82"]
acs: ["AC-111"]
files_hint: ["e2e/tests/telegram-link.spec.ts"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (ninth pass) — E1"
status: "done"
---

# T78 — An e2e test unlinks a connected account and sees the unlinked Toast and Connect Telegram

## Origin

Follow-up from the ninth-pass review: [`_review/review-2026-10-05.md`](../_review/review-2026-10-05.md), E1 (resolved "Fix now" by the user). Read those rows first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-111
- **Blocked by:** — · **Blocks:** T82

## What to change

- The only browser unlink today (`e2e/tests/telegram-link.spec.ts:230-240`, inside the "AC-117, AC-122" test) removes a Session lost account (the unconfirmed path) and doesn't check the Toast.
- Add a test titled with AC-111: link an account through the wizard, open Accounts, Unlink → "Unlink account", then assert the Toast "`<name>` is unlinked." (info) and that the Inbox shows "Connect Telegram" again (`test-plan.md:70`).
- In the existing test, assert the unconfirmed (error) Toast and tag it AC-113.

## RED first

E2e (Playwright, phone and desktop). The flow already works, so the first run may be green; prove the new assertion bites by changing the expected Toast text once, then restore it.

## Definition of Done

An e2e test tagged AC-111 links an account, unlinks it while it is connected, and checks the info Toast and SCR-10's Connect Telegram at both widths. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
