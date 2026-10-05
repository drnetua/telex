---
id: T19
title: "Build LinkedAccountSummary, port StatusBanner, extend CodeInput, Icon and PageFrame"
layer: "ui"
deps: ["T18"]
blocks: ["T20", "T22", "T23"]
acs: ["AC-01", "AC-122"]
files_hint: ["frontend/src/components/LinkedAccountSummary/", "frontend/src/components/StatusBanner/", "frontend/src/components/CodeInput/", "frontend/src/components/Icon/", "frontend/src/components/PageFrame/", "docs/design-system.md"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "done"
---

# T19 — Build LinkedAccountSummary, port StatusBanner, extend CodeInput, Icon and PageFrame

## Place in the sequence

- **Blocked by:** T18 — Add the SPA API clients, the single SSE live-update client and all new copy · **Blocks:** T20 — Build the SCR-02 wizard steps: phone, code, password with their validation and refusals, T22 — Make SCR-10 Inbox start linking and list one line per Linked Account, T23 — Build SCR-60 Accounts: the list with states, Add account, Sign in again and the unlink dialog · **Wave:** 2 — needs the data hooks and copy (T18).
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to see whether each Linked Account is connected (and whether it is still syncing its chats), is reconnecting after Telegram was unreachable, or has lost its session, and to sign in to it again when it has
> **So that** I know when teleX can work with an account and fix it without starting over
>
> — `spec.md §4, US-51, verbatim` · full text: [spec.md](../spec.md)

This task gives the Owner a consistent picture of each account's state and a banner on every signed-in screen when one has lost its session.

## Inlined context

> `LinkedAccountSummary`: One Linked Account's name, masked phone, state `Badge` (connected / reconnecting / session lost, icon + words) and chat-sync line (Tabler `progress` + count, or the chat count once synced). `variant="line"` (SCR-10, compact, the whole row links to SCR-60, no actions) and `variant="row"` (SCR-60, with an actions slot). The badge region is `aria-live="polite"`.
> `StatusBanner` (C-04) — ported reference: one condition, or the most severe plus "+N more"; one action; no close button while its cause holds; icon + words; `role="status"`.
> `CodeInput` — adds `length?: number` (default 6, so E01 is unchanged) and a `label` prop.
> `PageFrame` — an "Accounts" `Button` ghost `icon="brand-telegram"` before "Profile and security", icon-only with `ariaLabel` on phone and 44 px targets, which opens SCR-60; a banner slot directly under the header. `Icon` — `unlink`.
>
> — `screens.md §New components + §Shared conventions, abridged` · full text: [screens.md](../screens.md)

> Account disconnected banner: whenever `listMyLinkedAccounts` has an account in `session_lost`. No close button.
> - One lost account: icon `alert-circle` + "`<displayName>`'s Telegram is disconnected. teleX can't work with it until you sign in again." Action "Sign in again" calls `startMyLinkingAttempt` with `origin: accounts` and `targetLinkedAccountId`, and opens SCR-02.
> - Several lost accounts: "`<n>` Telegram accounts are disconnected." Action "Open Accounts" goes to SCR-60.
> Masked phone: `+<countryCode> ••• ••<lastDigits>`. Row states: connected-syncing (`Badge tone="success" icon="check"` "Connected", progress + "Syncing chats: `<chatsSynced>` of `<chatsTotal>`", indeterminate while total null), connected-synced ("`<chatsSynced>` chats"), reconnecting (`tone="neutral" icon="refresh"`), session-lost (`tone="danger" icon="alert-circle"` + "Sign in again").
>
> — `screens.md §Shared conventions + SCR-60 rows, abridged` · full text: [screens.md](../screens.md)

> **Hard rule:** Status never by color alone; `ai` purple only for AI content; tokens only (no raw hex).
>
> — `CLAUDE.md §Quality gates, abridged` · full text: [CLAUDE.md](../../../../CLAUDE.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Consumes `listMyLinkedAccounts` (via T18's hook) and `startMyLinkingAttempt` (banner action). Internal otherwise.

## Acceptance criteria

### AC-01 — happy

> **Given** a signed-in Owner with no Linked Account, whose Telegram account has two-step verification turned on
> **When** the Owner starts "Connect Telegram", types their phone number, types the code Telegram sent to their other devices and then types their password
> **Then** the account is shown as connected as soon as the sign-in completes, with its Telegram name and a masked phone number (only the country code and the last two digits visible), its chat list starts syncing with visible progress, and the Inbox no longer shows the "Connect Telegram" step; an account without two-step verification skips the password step
>
> — `spec.md §5, AC-01, verbatim` · full text: [spec.md](../spec.md)

### AC-122 — error

> **Given** an Owner with a connected Linked Account
> **When** teleX temporarily can't reach Telegram, or Telegram confirms that the account's session has ended
> **Then** while Telegram is unreachable the account shows "Reconnecting" on the Accounts page and teleX reconnects on its own without asking the Owner for anything; only when Telegram confirms the session has ended does the account show "Session lost", and then every signed-in screen also shows a Status Banner saying the account is disconnected, with a "Sign in again" action, until the Owner signs in again or unlinks it
>
> — `spec.md §5, AC-122, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `components/LinkedAccountSummary/` with `line` and `row` variants, the four state presentations, masked phone, sync line, `aria-live="polite"` on the badge
- [ ] `components/StatusBanner/` (C-04 port) + an `AccountDisconnectedBanner` condition reading `linked-accounts`; one vs several lost accounts
- [ ] `CodeInput`: `length` (default 6) + `label`; E01 usage unchanged
- [ ] `Icon`: add `IconUnlink`; `PageFrame`: Accounts button + banner slot rendering the banner
- [ ] Update `docs/design-system.md` inventory rows for the new/extended components (implement registers them)
- [ ] Vitest for each component state

## Edge cases

| Case | Behaviour |
|---|---|
| `chatsTotal` null while syncing | Indeterminate progress + "Syncing chats" |
| Two accounts lost | Banner "2 Telegram accounts are disconnected." + "Open Accounts" |
| No account lost | No banner |

## Definition of Done

- [ ] Vitest for every component state passes; E01 `CodeInput` tests still pass
- [ ] `pnpm run check` clean
- [ ] every Hard Rule inlined above still holds
