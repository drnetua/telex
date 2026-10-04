---
id: T14
title: "Expose list and unlink of my Linked Accounts, and back getMe.linkedAccountCount with messaging"
layer: "ports"
deps: ["T7", "T11"]
blocks: ["T25"]
acs: ["AC-03", "AC-110", "AC-113", "AC-114"]
files_hint: ["backend/app/src/main/kotlin/telex/web/api/LinkedAccountsController.kt", "backend/app/src/main/kotlin/telex/web/api/MeController.kt", "backend/app/src/main/kotlin/telex/identity/OwnerProfiles.kt", "backend/app/src/integrationTest/kotlin/telex/web/LinkedAccountsApiIT.kt"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "done"
---

# T14 — Expose list and unlink of my Linked Accounts, and back getMe.linkedAccountCount with messaging

## Place in the sequence

- **Blocked by:** T7 — Build the LinkedAccount aggregate: rules, states, masked phone, repository, events and the account limit, T11 — Unlink an account: bounded sign-out, one-transaction delete with AccountUnlinked, then destroy the session · **Blocks:** T25 — Add Playwright end-to-end tests of linking, live state and unlink at 360 px and 1280 px with axe · **Wave:** 4 — needs the read API (T7) and unlink (T11).
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to link more than one Telegram account, up to the installation's limit, and see all of them in one list
> **So that** my personal and work accounts both work in teleX
>
> — `spec.md §4, US-50, verbatim` · full text: [spec.md](../spec.md)

This task lets the Owner see all their accounts in one list and unlink one, while another Owner's accounts stay invisible.

## Inlined context

> `web` (interface) adds the account and wizard endpoints and the SSE stream, and calls only `messaging` and `identity` (ADR-0005).
> Authorization: Another Owner's account behaves as missing, with the same `not-found` problem, for list, get, re-sign-in, unlink and chats (AC-03).
>
> — `sad.md §5 + §8 Authorization, abridged` · full text: [sad.md](../sad.md)

> `getMe.linkedAccountCount` (E01 contract) becomes real in E02. `identity.OwnerProfiles.me` can't count Linked Accounts: `messaging` → `identity` (OwnerKeys) already exists, so `identity` → `messaging` would be a cycle. `MeController` (in `web`) should take the count from `messaging.LinkedAccounts` instead, and the field leaves `OwnerProfiles`.
>
> — `contracts/api-sync-report.md §C 6, verbatim` · full text: [api-sync-report.md](../contracts/api-sync-report.md)

> **Hard rule:** Every Linked Account operation looks only among the caller's own accounts. Another Owner's account and an id that never existed answer the same `404 not-found` (AC-03).
>
> — `contracts/openapi.yaml info.description, verbatim` · full text: [openapi.yaml](../contracts/openapi.yaml)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes. (Reads `linked_account` + `COUNT(channel)` through T7; unlink writes through T11.)

## API contract

- `listMyLinkedAccounts` `GET /api/v1/linked-accounts` → `200 {items: [{id, displayName, phone: {countryCode, lastDigits}, state, chatSync: {chatsSynced, chatsTotal, completedAt}, linkedAt}]}` oldest first, Session lost included; accepts `X-Telex-Background: 1` · `401`.
- `unlinkMyLinkedAccount` `DELETE /api/v1/linked-accounts/{linkedAccountId}` (+ `X-XSRF-TOKEN`) → `200 {signOutConfirmed}` · `401`, `403 forbidden`, `404 not-found`.
- `getMe` unchanged in shape: `linkedAccountCount` now the caller's real count.

— `contracts/openapi.yaml, operationIds listMyLinkedAccounts / unlinkMyLinkedAccount, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-03 — authorization

> **Given** two Owners, each with their own Linked Account
> **When** one Owner tries to see, re-sign-in to or unlink the other Owner's Linked Account, or to see any chat synced for it, by any means
> **Then** teleX behaves as if that account and those chats don't exist; and the Operator sees no Telegram name, phone number or chat of any Owner's Linked Account
>
> — `spec.md §5, AC-03, verbatim` · full text: [spec.md](../spec.md)

### AC-110 — cross-context

> **Given** an Owner with a connected Linked Account
> **When** they sign out of teleX, or their Sign-in Session ends or is revoked
> **Then** the Linked Account stays connected and keeps syncing, and the next time they sign in it is still there; and an Owner whose Sign-in Session ends in the middle of the linking wizard must sign in again and start the wizard over, with no session kept from the unfinished attempt
>
> — `spec.md §5, AC-110, verbatim` · full text: [spec.md](../spec.md)

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

- [ ] `web/api/LinkedAccountsController`: list + unlink, `OwnerId` from the security principal, `Cache-Control: no-store`
- [ ] `MeController` takes the count from `messaging.LinkedAccounts.countMine`; remove `linkedAccountCount` from `identity.OwnerProfiles`
- [ ] `LinkedAccountsApiIT` with `ContractValidator`: list for two Owners (each sees only theirs), unlink confirmed / unconfirmed, 404 for another Owner's id and an unknown id (byte-identical problem), 403 without CSRF, accounts still listed after sign-out + sign-in (AC-110)

## Edge cases

| Case | Behaviour |
|---|---|
| Another Owner's id on DELETE | `404 not-found`, same body as a random id (AC-03) |
| Unlink with Telegram unreachable | `200 {signOutConfirmed: false}` (AC-113) |
| Background refetch | `X-Telex-Background: 1` doesn't bump session activity |

## Definition of Done

- [ ] `LinkedAccountsApiIT` passes; every response validates against `openapi.yaml`
- [ ] `ModularityTest` green (`web` → `messaging`, no `identity` → `messaging`)
- [ ] detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
