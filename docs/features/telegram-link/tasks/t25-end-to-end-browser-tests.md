---
id: T25
title: "Add Playwright end-to-end tests of linking, live state and unlink at 360 px and 1280 px with axe"
layer: "tests"
deps: ["T14", "T15", "T17", "T21", "T22", "T23"]
blocks: []
acs: ["AC-01", "AC-02", "AC-109", "AC-117", "AC-122"]
files_hint: ["e2e/tests/telegram-link.spec.ts", "e2e/support/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "done"
---

# T25 — Add Playwright end-to-end tests of linking, live state and unlink at 360 px and 1280 px with axe

## Place in the sequence

- **Blocked by:** T14 — Expose list and unlink of my Linked Accounts, and back getMe.linkedAccountCount with messaging, T15 — Expose the linking-attempt endpoints with the contract's problem codes, validation and Retry-After, T17 — Wire the Operator config, the session volume and the README Telegram setup step, T21 — Build the SCR-02 outcomes: wait countdown, refusals, attempt ended, cancel and success, T22 — Make SCR-10 Inbox start linking and list one line per Linked Account, T23 — Build SCR-60 Accounts: the list with states, Add account, Sign in again and the unlink dialog · **Blocks:** — · **Wave:** 7 — last — needs the endpoints, config and all three screens.
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to link my Telegram account to teleX with my phone number, the code Telegram sends me and my password if I have one
> **So that** teleX can work with my chats on my behalf
>
> — `spec.md §4, US-02, verbatim` · full text: [spec.md](../spec.md)

This task proves the whole Owner journey works in a real browser at phone and desktop widths and meets WCAG 2.2 AA.

## Inlined context

> QG-3c: Playwright runs with the `fake` adapter at both widths, plus an automated accessibility scan (axe) with 0 violations. The reload-mid-wizard and second-tab paths (AC-109) are e2e cases.
> Spec §6: the wizard, the Accounts page and the unlink dialog work at 360 px and 1280 px and meet WCAG 2.2 AA.
>
> — `sad.md §10, QG-3c + spec.md §6, abridged` · full text: [sad.md](../sad.md)

> The `fake` Telegram adapter's accounts use Telegram's test-number shape (`99966XYYYY`), never a real-looking number.
>
> — `data-model.md §Test fixtures, verbatim` · full text: [data-model.md](../data-model.md)

> **Hard rule:** Every touched screen works at phone and desktop widths and implements all states from the feature's `screens.md`.
>
> — `CLAUDE.md §Quality gates, verbatim` · full text: [CLAUDE.md](../../../../CLAUDE.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Drives the SPA only; the fake adapter is scripted by the test phone numbers fixed in T4.

## Acceptance criteria

### AC-01 — happy

> **Given** a signed-in Owner with no Linked Account, whose Telegram account has two-step verification turned on
> **When** the Owner starts "Connect Telegram", types their phone number, types the code Telegram sent to their other devices and then types their password
> **Then** the account is shown as connected as soon as the sign-in completes, with its Telegram name and a masked phone number (only the country code and the last two digits visible), its chat list starts syncing with visible progress, and the Inbox no longer shows the "Connect Telegram" step; an account without two-step verification skips the password step
>
> — `spec.md §5, AC-01, verbatim` · full text: [spec.md](../spec.md)

### AC-02 — error

> **Given** an Owner in the linking wizard who has been sent a code
> **When** they type a wrong or expired code
> **Then** the wizard says the code is wrong or expired, lets them try again or ask for a new code, and when Telegram limits the attempts the wizard ends the attempt and says when the Owner can try again, counting down to that time; starting again with the same number before then shows the remaining wait instead of sending a code
>
> — `spec.md §5, AC-02, verbatim` · full text: [spec.md](../spec.md)

### AC-109 — domain invariant

> **Given** an Owner who started the linking wizard
> **When** they cancel it, or take no step in it for 15 minutes
> **Then** the attempt is discarded, no teleX device from it remains in the account's active sessions in Telegram, and starting again begins with the phone number; until then an Owner has at most one open attempt, and reloading the page or opening the wizard in another tab or device continues it at the step where it stopped
>
> — `spec.md §5, AC-109, verbatim` · full text: [spec.md](../spec.md)

### AC-117 — error

> **Given** an Owner with a connected Linked Account
> **When** the teleX session is ended from the Telegram app or by Telegram itself
> **Then** within 5 minutes the account shows "Session lost" with a "Sign in again" action; signing in again with the same Telegram account brings back the same Linked Account with everything attached to it, while signing in with a different Telegram account at that point is refused, that sign-in is ended so no teleX device remains in Telegram, and the Owner is told to link it as a new account (or, if it belongs to another Owner, sees the rule from AC-04)
>
> — `spec.md §5, AC-117, verbatim` · full text: [spec.md](../spec.md)

### AC-122 — error

> **Given** an Owner with a connected Linked Account
> **When** teleX temporarily can't reach Telegram, or Telegram confirms that the account's session has ended
> **Then** while Telegram is unreachable the account shows "Reconnecting" on the Accounts page and teleX reconnects on its own without asking the Owner for anything; only when Telegram confirms the session has ended does the account show "Session lost", and then every signed-in screen also shows a Status Banner saying the account is disconnected, with a "Sign in again" action, until the Owner signs in again or unlinks it
>
> — `spec.md §5, AC-122, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `e2e/support/`: sign-up helper reuse (mailpit) + a helper to pick fake test numbers (2FA, wait, terminate)
- [ ] `e2e/tests/telegram-link.spec.ts` at both widths: link with 2FA → Inbox line syncing; wrong code → retry; wait countdown; reload + second tab continue the step; terminate → Session lost + banner → Sign in again; unlink → Inbox shows Connect Telegram
- [ ] axe scan on each visited state; 0 violations

## Edge cases

| Case | Behaviour |
|---|---|
| Session lost detection window | Fake terminates immediately; assert within the polling timeout, not 5 min |

## Definition of Done

- [ ] the spec passes at 360 px and 1280 px in CI with 0 axe violations
- [ ] every Hard Rule inlined above still holds
