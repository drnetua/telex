---
id: T22
title: "Make SCR-10 Inbox start linking and list one line per Linked Account"
layer: "ui"
deps: ["T19"]
blocks: ["T25"]
acs: ["AC-01", "AC-119"]
files_hint: ["frontend/src/pages/inbox/", "frontend/src/app/AppRoutes.tsx"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "done"
---

# T22 — Make SCR-10 Inbox start linking and list one line per Linked Account

## Place in the sequence

- **Blocked by:** T19 — Build LinkedAccountSummary, port StatusBanner, extend CodeInput, Icon and PageFrame · **Blocks:** T25 — Add Playwright end-to-end tests of linking, live state and unlink at 360 px and 1280 px with axe · **Wave:** 3 — needs the components (T19); shares `AppRoutes.tsx` with T20/T23 — serialized.
- **Lane:** shares `frontend/src/app/AppRoutes.tsx` with T20; shares `frontend/src/app/AppRoutes.tsx` with T23 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to link my Telegram account to teleX with my phone number, the code Telegram sends me and my password if I have one
> **So that** teleX can work with my chats on my behalf
>
> — `spec.md §4, US-02, verbatim` · full text: [spec.md](../spec.md)

This task turns the Inbox's dead "Connect Telegram" step into the start of linking, and shows the linked accounts once there are any.

## Inlined context

> SCR-10: "Connect Telegram" now starts linking (E01's `note` Toast "Telegram linking is coming next." is removed), and the page lists the Linked Accounts. The page reads `listMyLinkedAccounts`. `Me.linkedAccountCount` is no longer what drives it.
> | loading | `PageFrame` + `LoadState state="loading"` |
> | default (empty) | 0 Linked Accounts: h1 "Inbox", `EmptyState kind="first"` (icon `brand-telegram`) "Connect your Telegram account to start.", primary "Connect Telegram" |
> | starting | `startMyLinkingAttempt { origin: inbox }` → busy "Starting" |
> | start-refused | `503 telegram-linking-not-set-up` → Toast error "Telegram linking isn't set up on this installation yet. The person who runs teleX has to finish the setup." The wizard doesn't open |
> | resumed | `200` / `201` → SCR-02 |
> | with-accounts | ≥ 1: `card` + `list-group` of `LinkedAccountSummary variant="line"`, each a link to SCR-60 |
> | linked | arrived from SCR-02 success → with-accounts + the success Toast |
> | live / banner | background refetch in place / `StatusBanner` when any account is `session_lost` |
> | error | `401` → SCR-01 / SCR-92; `5xx` or no answer in 10 s → SCR-93 |
>
> — `screens.md §SCR-10, abridged` · full text: [screens.md](../screens.md)

> **Hard rule:** All strings in `messages.ts`; status never by color alone.
>
> — `CLAUDE.md §Quality gates, abridged` · full text: [CLAUDE.md](../../../../CLAUDE.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Consumes `listMyLinkedAccounts` and `startMyLinkingAttempt` (`origin: inbox`) via T18.

## Acceptance criteria

### AC-01 — happy

> **Given** a signed-in Owner with no Linked Account, whose Telegram account has two-step verification turned on
> **When** the Owner starts "Connect Telegram", types their phone number, types the code Telegram sent to their other devices and then types their password
> **Then** the account is shown as connected as soon as the sign-in completes, with its Telegram name and a masked phone number (only the country code and the last two digits visible), its chat list starts syncing with visible progress, and the Inbox no longer shows the "Connect Telegram" step; an account without two-step verification skips the password step
>
> — `spec.md §5, AC-01, verbatim` · full text: [spec.md](../spec.md)

### AC-119 — error

> **Given** an installation whose Operator hasn't given it Telegram app credentials
> **When** an Owner chooses "Connect Telegram" or "Add account"
> **Then** teleX says that Telegram linking isn't set up on this installation yet and that the Operator has to finish the setup, and doesn't start the wizard
>
> — `spec.md §5, AC-119, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `pages/inbox/InboxPage.tsx`: switch to `listMyLinkedAccounts`; remove the E01 "coming next" Toast
- [ ] Connect Telegram → start → navigate to `/connect-telegram`; start-refused Toast
- [ ] with-accounts list of `LinkedAccountSummary variant="line"`; success Toast from router state
- [ ] Update `InboxPage.test.tsx` for every state

## Edge cases

| Case | Behaviour |
|---|---|
| Last account unlinked | Empty state with "Connect Telegram" again (AC-111) |
| Open attempt exists | `200` → SCR-02 at its step |

## Definition of Done

- [ ] Vitest for every SCR-10 state passes
- [ ] `pnpm run check` clean
- [ ] every Hard Rule inlined above still holds
