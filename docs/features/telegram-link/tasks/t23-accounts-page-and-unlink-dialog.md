---
id: T23
title: "Build SCR-60 Accounts: the list with states, Add account, Sign in again and the unlink dialog"
layer: "ui"
deps: ["T19"]
blocks: ["T25"]
acs: ["AC-03", "AC-111", "AC-113", "AC-114"]
files_hint: ["frontend/src/pages/accounts/", "frontend/src/app/AppRoutes.tsx"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T23 — Build SCR-60 Accounts: the list with states, Add account, Sign in again and the unlink dialog

## Place in the sequence

- **Blocked by:** T19 — Build LinkedAccountSummary, port StatusBanner, extend CodeInput, Icon and PageFrame · **Blocks:** T25 — Add Playwright end-to-end tests of linking, live state and unlink at 360 px and 1280 px with axe · **Wave:** 3 — needs the components (T19); shares `AppRoutes.tsx` with T20/T22 — serialized.
- **Lane:** shares `frontend/src/app/AppRoutes.tsx` with T20; shares `frontend/src/app/AppRoutes.tsx` with T22 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to unlink a Linked Account so that teleX signs out of it and deletes everything it stored for it
> **So that** no copy of my Telegram access stays in teleX
>
> — `spec.md §4, US-03, verbatim` · full text: [spec.md](../spec.md)

This task gives the Owner one place to see every account, add another, bring a lost one back, and unlink one completely.

## Inlined context

> SCR-60 lists the Owner's Linked Accounts, oldest first. h1 "Accounts", "Telegram accounts teleX works with. Each one syncs its own chats."; `card` header "Linked accounts" + secondary `icon="plus"` "Add account"; `list-group` of `LinkedAccountSummary variant="row"` with "Unlink" (ghost `icon="unlink"`) and, for session-lost, primary small "Sign in again".
> | empty | 0 accounts reached directly: `EmptyState kind="first"` "No Telegram accounts linked yet." + "Add account"; header button hidden |
> | starting | "Add account" (`origin: accounts`) or "Sign in again" (+ `targetLinkedAccountId`) → busy "Starting" |
> | start-refused | Toast error: `linked-account-limit-reached` "You've linked `<limit>` accounts, the most this installation allows. Unlink an account to add another."; `telegram-linking-not-set-up` as SCR-10; `telegram-account-already-linked` "This account is already connected." + refetch; `404 not-found` → refetch, row disappears, no message (AC-03) |
> | unlink-confirm | `ConfirmDialog tone="danger"`: "Unlink `<displayName>`?"; "teleX will sign out of this Telegram account and delete its session and the `<chatsSynced>` chats it synced. To use it in teleX again, you'll link it from the start."; "Unlink account" / "Keep account" |
> | unlinking | busy "Unlinking" (up to 10 s); cancel disabled |
> | unlinked | `{signOutConfirmed: true}`: dialog closes, refetch, Toast info "`<displayName>` is unlinked."; list now empty → SCR-10 |
> | unlinked-unconfirmed | `{signOutConfirmed: false}`: as unlinked, error Toast (stays): "`<displayName>` is unlinked and teleX deleted everything it kept. Telegram couldn't confirm the sign-out, so check Active sessions in the Telegram app and end teleX there if it's listed." |
> | unlink-gone | `404 not-found`: dialog closes, refetch, no message |
> | linked / live / banner / error | success Toast / in-place refetch / `StatusBanner` / shared failure routing |
>
> — `screens.md §SCR-60, abridged` · full text: [screens.md](../screens.md)

> **Hard rule:** The unlink confirmation is the ordinary C-33 dialog that names the consequences, not the destructive variant where the Owner types the name.
>
> — `spec.md §1, Decision deviation, abridged` · full text: [spec.md](../spec.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Consumes `listMyLinkedAccounts`, `unlinkMyLinkedAccount`, `startMyLinkingAttempt` (`origin: accounts`, optional `targetLinkedAccountId`) via T18.

## Acceptance criteria

### AC-03 — authorization

> **Given** two Owners, each with their own Linked Account
> **When** one Owner tries to see, re-sign-in to or unlink the other Owner's Linked Account, or to see any chat synced for it, by any means
> **Then** teleX behaves as if that account and those chats don't exist; and the Operator sees no Telegram name, phone number or chat of any Owner's Linked Account
>
> — `spec.md §5, AC-03, verbatim` · full text: [spec.md](../spec.md)

### AC-111 — happy

> **Given** an Owner with a connected Linked Account
> **When** they choose to unlink it and confirm in a dialog that names what will happen
> **Then** teleX signs out of that Telegram account so the teleX device disappears from the account's active sessions in Telegram, deletes the account's session and every chat-list entry it synced, and removes the account from the list; if it was the Owner's last Linked Account, the Inbox shows the "Connect Telegram" step again
>
> — `spec.md §5, AC-111, verbatim` · full text: [spec.md](../spec.md)

### AC-113 — error

> **Given** an Owner whose Linked Account has lost its session, or whose Telegram can't be reached at that moment
> **When** they unlink it
> **Then** teleX still deletes everything it stored for the account and removes it from the list, and tells the Owner that Telegram couldn't confirm the sign-out, so they should check active sessions in the Telegram app
>
> — `spec.md §5, AC-113, verbatim` · full text: [spec.md](../spec.md)

### AC-114 — happy

> **Given** an Owner with one Linked Account and an installation limit of more than one
> **When** they add another account from the Accounts page and complete the wizard
> **Then** both accounts appear in the Accounts list, each with its Telegram name, masked phone number and state, and each syncs its own chat list
>
> — `spec.md §5, AC-114, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Route `/accounts` in `app/AppRoutes.tsx`; `pages/accounts/AccountsPage.tsx`
- [ ] Rows with actions; Add account and Sign in again → start → `/connect-telegram`; start-refused Toasts
- [ ] Unlink with `ConfirmDialog`, busy state, confirmed / unconfirmed Toasts, empty-after-unlink → `/inbox`
- [ ] Vitest per state

## Edge cases

| Case | Behaviour |
|---|---|
| Row unlinked in another tab | Disappears on the live refetch |
| 404 on unlink or Sign in again | Row disappears silently (AC-03) |
| Phone width | Row actions drop below the text, full width; dialog as full-width sheet |

## Definition of Done

- [ ] Vitest for every SCR-60 state passes
- [ ] `pnpm run check` clean
- [ ] every Hard Rule inlined above still holds
