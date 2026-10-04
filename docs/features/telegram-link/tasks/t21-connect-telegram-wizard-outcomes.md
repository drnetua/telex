---
id: T21
title: "Build the SCR-02 outcomes: wait countdown, refusals, attempt ended, cancel and success"
layer: "ui"
deps: ["T20"]
blocks: ["T25"]
acs: ["AC-04", "AC-108", "AC-109", "AC-115", "AC-117"]
files_hint: ["frontend/src/pages/connect-telegram/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "done"
---

# T21 — Build the SCR-02 outcomes: wait countdown, refusals, attempt ended, cancel and success

## Place in the sequence

- **Blocked by:** T20 — Build the SCR-02 wizard steps: phone, code, password with their validation and refusals · **Blocks:** T25 — Add Playwright end-to-end tests of linking, live state and unlink at 360 px and 1280 px with axe · **Wave:** 4 — extends T20's page (same directory — serialized).
- **Lane:** shares `frontend/src/pages/connect-telegram/` with T20 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to link my Telegram account to teleX with my phone number, the code Telegram sends me and my password if I have one
> **So that** teleX can work with my chats on my behalf
>
> — `spec.md §4, US-02, verbatim` · full text: [spec.md](../spec.md)

This task tells the Owner plainly how an attempt ended, and returns them to where they started.

## Inlined context

> | State | Trigger | Shows |
> |---|---|---|
> | wait | `429 telegram-wait-required` (attempt ended) | h1 "Too many attempts"; `EmptyState kind="blocked"` icon `clock` "Telegram asks you to wait. You can try again at `<retryAt HH:mm>`, in `<m:ss>`."; countdown every second, announced only at start and end; secondary "Back" → origin; at zero "You can try again now." + primary "Start again" |
> | refused-other-owner | `409 telegram-account-owned-by-another-owner` | h1 "This account is linked elsewhere"; icon `ban`; "This Telegram account is already linked to another teleX account. One Telegram account belongs to one person. teleX has signed out of it again."; "Back" |
> | refused-already-linked | `409 telegram-account-already-linked` | h1 "Already linked"; icon `check`; "This Telegram account is already one of your accounts. teleX has signed out of the extra sign-in."; "Back" |
> | refused-limit | `409 linked-account-limit-reached` | h1 "Account limit reached"; "You've linked `<limit>` accounts, the most this installation allows. teleX has signed out of this one. Unlink an account to free a place."; "Open Accounts" |
> | refused-mismatch | `409 telegram-account-mismatch` | h1 "A different account"; "You signed in to a different Telegram account, not `<displayName>`. teleX has signed out of it. To use it, add it as a new account."; "Back to Accounts" |
> | attempt-ended | `404 linking-attempt-not-found` | h1 "This linking has ended"; icon `clock`; "It was cancelled, finished in another window, or left for 15 minutes."; primary "Start again" + ghost "Back" |
> | starting-again | "Start again" | busy "Starting"; `201` → phone; `503`/`409` → Toast error with the SCR-10 start-refused copy |
> | cancelling | "Cancel" | busy "Cancelling" → `204` → origin, no message |
> | success | `linked` / `signed-in-again` | → origin (SCR-10 `inbox`, SCR-60 `accounts`) + Toast info "`<displayName>` is connected. teleX is syncing its chats." / "`<displayName>` is connected again." |
> | error | `401` → SCR-01 / SCR-92; unmapped `5xx`, `403`, no answer in 10 s → SCR-93 | shared failure routing |
>
> — `screens.md §SCR-02 states table (outcome states), abridged` · full text: [screens.md](../screens.md)

> **Hard rule:** Status never by color alone (icon + words); copy only from `messages.ts`.
>
> — `CLAUDE.md §Quality gates, abridged` · full text: [CLAUDE.md](../../../../CLAUDE.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Consumes `startMyLinkingAttempt`, `cancelMyLinkingAttempt` and the outcomes/refusals of `submitLinkingCode` / `submitLinkingPassword` (`LinkingStepResult`, `409` codes, `429` + `retryAt`, `404 linking-attempt-not-found`) via T18.

## Acceptance criteria

### AC-04 — domain invariant

> **Given** a Telegram account that is already a Linked Account of another Owner
> **When** an Owner completes the linking wizard for that same Telegram account
> **Then** teleX blocks the link with the rule "one Telegram account belongs to one Owner", ends the sign-in it just made so no teleX device for the second Owner remains in the account's active sessions in Telegram, and leaves the other Owner's Linked Account untouched
>
> — `spec.md §5, AC-04, verbatim` · full text: [spec.md](../spec.md)

### AC-108 — domain invariant

> **Given** an Owner whose Telegram account is already one of their Linked Accounts
> **When** they go through the linking wizard for the same Telegram account again
> **Then** teleX doesn't create a second Linked Account: if that account is connected, teleX ends the sign-in it just made, so no extra teleX device remains in the account's active sessions in Telegram, and tells them the account is already linked; if that account has lost its session, the wizard counts as "Sign in again" and brings back the same Linked Account (AC-117). A Telegram account is recognized by its identity in Telegram, not by its phone number, so a changed phone number still matches
>
> — `spec.md §5, AC-108, verbatim` · full text: [spec.md](../spec.md)

### AC-109 — domain invariant

> **Given** an Owner who started the linking wizard
> **When** they cancel it, or take no step in it for 15 minutes
> **Then** the attempt is discarded, no teleX device from it remains in the account's active sessions in Telegram, and starting again begins with the phone number; until then an Owner has at most one open attempt, and reloading the page or opening the wizard in another tab or device continues it at the step where it stopped
>
> — `spec.md §5, AC-109, verbatim` · full text: [spec.md](../spec.md)

### AC-115 — domain invariant

> **Given** an Owner who already has as many Linked Accounts as the installation's limit allows
> **When** they try to add another account, or finish a wizard after another account has taken the last place
> **Then** teleX doesn't start the wizard, or ends the sign-in it just made so no teleX device remains in Telegram, and tells them the limit and that they can unlink an account to free a place; every Linked Account counts toward the limit, including one that has lost its session, signing in again to a lost-session account takes no new place, and lowering the limit never unlinks existing accounts
>
> — `spec.md §5, AC-115, verbatim` · full text: [spec.md](../spec.md)

### AC-117 — error

> **Given** an Owner with a connected Linked Account
> **When** the teleX session is ended from the Telegram app or by Telegram itself
> **Then** within 5 minutes the account shows "Session lost" with a "Sign in again" action; signing in again with the same Telegram account brings back the same Linked Account with everything attached to it, while signing in with a different Telegram account at that point is refused, that sign-in is ended so no teleX device remains in Telegram, and the Owner is told to link it as a new account (or, if it belongs to another Owner, sees the rule from AC-04)
>
> — `spec.md §5, AC-117, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `WaitState` with a countdown from `retryAt` (live region announces start and end only)
- [ ] `RefusedState` per refusal code; `AttemptEndedState` with "Start again" (last known origin, `inbox` if none)
- [ ] Cancel flow and success redirect to `origin` with the Toast (pass via router state)
- [ ] Vitest per state, including the countdown reaching zero (fake timers)

## Edge cases

| Case | Behaviour |
|---|---|
| Countdown reaches zero | "You can try again now." + primary "Start again" |
| Cancel when the attempt is already gone | `204` → origin, no message (AC-109) |
| Sign-in Session ended mid-wizard | `401 session-ended` → SCR-92; next start begins at the phone step (AC-110) |

## Definition of Done

- [ ] Vitest for every state listed passes
- [ ] `pnpm run check` clean
- [ ] every Hard Rule inlined above still holds
