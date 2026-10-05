---
id: T18
title: "Add the SPA API clients, the single SSE live-update client and all new copy"
layer: "ui"
deps: []
blocks: ["T19"]
acs: ["AC-116", "AC-121", "AC-122"]
files_hint: ["frontend/src/api/linkedAccounts.ts", "frontend/src/api/linking.ts", "frontend/src/api/live.ts", "frontend/src/api/client.ts", "frontend/src/App.tsx", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "done"
---

# T18 — Add the SPA API clients, the single SSE live-update client and all new copy

## Place in the sequence

- **Blocked by:** — · **Blocks:** T19 — Build LinkedAccountSummary, port StatusBanner, extend CodeInput, Icon and PageFrame · **Wave:** 1 — no deps — written against `openapi.yaml`, so the SPA lane starts in wave 1.
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to see whether each Linked Account is connected (and whether it is still syncing its chats), is reconnecting after Telegram was unreachable, or has lost its session, and to sign in to it again when it has
> **So that** I know when teleX can work with an account and fix it without starting over
>
> — `spec.md §4, US-51, verbatim` · full text: [spec.md](../spec.md)

This task gives every screen of the feature one data path (REST) and one live channel, so state changes show without reloads.

## Inlined context

> **Live state.** The SPA keeps one `openLiveUpdates` stream per tab. On a `linked-accounts` hint it refetches `listMyLinkedAccounts` in the background (`X-Telex-Background: 1`), and on a stream reconnect it refetches everything it shows (ADR-0005). The refetch replaces the data in place: no loading skeleton and no Toast.
> **Copy.** All strings go in `frontend/src/messages.ts` (new `messages.linking`, `messages.accounts` and `messages.banner` groups). […] Sentence case, no emoji, no exclamation marks.
> **Failure routing:** `401 unauthenticated` → SCR-01; `401 session-ended` → SCR-92; no answer within 10 s, an unmapped `5xx`, or `403` → SCR-93. The E02-specific `503`s […] and every `404`, `409`, `422` and `429` are handled on the screen.
>
> — `screens.md §Shared conventions, abridged` · full text: [screens.md](../screens.md)

> The SPA's single SSE client turns each hint into a TanStack Query `invalidateQueries`, which refetches through the normal REST endpoints. […] After any reconnect the SPA invalidates everything it shows, so a missed hint never leaves stale state.
>
> — `adr/0005 §Considered options 1 + §Decision outcome, abridged` · full text: [0005-push-live-state-to-the-spa-as-sse-invalidation-hints.md](../adr/0005-push-live-state-to-the-spa-as-sse-invalidation-hints.md)

> **Hard rule:** UI follows the design-system README: tokens only, sentence-case English copy with no emoji, all strings in one message catalog.
>
> — `CLAUDE.md §Quality gates, abridged` · full text: [CLAUDE.md](../../../../CLAUDE.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Consumes all ten operations of `contracts/openapi.yaml` (tags `linked-accounts`, `linking`, `live`): types for `LinkedAccount`, `ChatSync`, `MaskedPhone`, `LinkingAttempt`, `LinkingStepResult`, `UnlinkResult`, `Problem` (+ `retryAt`, `passwordHint`, `limit`, `step`) and the `ErrorCode` enum.

— `contracts/openapi.yaml, components.schemas, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-116 — happy

> **Given** an Owner who just linked an account
> **When** its chat list is syncing
> **Then** the connected account shows the sync progress as chats synced out of the total, where the total counts every chat of the account, archived ones included, and when the sync finishes it shows the number of chats; the Owner can leave the page and come back without stopping the sync, and if teleX restarts during the sync it continues from where it stopped instead of starting over
>
> — `spec.md §5, AC-116, verbatim` · full text: [spec.md](../spec.md)

### AC-121 — happy

> **Given** an Owner with a connected Linked Account whose chat list has finished syncing
> **When** in Telegram they join or leave a chat, a chat is renamed, or new messages arrive
> **Then** within one minute the number of chats shown for the account reflects the change, and the chat list teleX keeps for the account stays current while it is connected; showing that list and the messages is E04
>
> — `spec.md §5, AC-121, verbatim` · full text: [spec.md](../spec.md)

### AC-122 — error

> **Given** an Owner with a connected Linked Account
> **When** teleX temporarily can't reach Telegram, or Telegram confirms that the account's session has ended
> **Then** while Telegram is unreachable the account shows "Reconnecting" on the Accounts page and teleX reconnects on its own without asking the Owner for anything; only when Telegram confirms the session has ended does the account show "Session lost", and then every signed-in screen also shows a Status Banner saying the account is disconnected, with a "Sign in again" action, until the Owner signs in again or unlinks it
>
> — `spec.md §5, AC-122, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `api/linkedAccounts.ts` (query key `linked-accounts`, list + unlink) and `api/linking.ts` (get/start/cancel/phone/code/resend/password) on the existing `api/client.ts`; typed problem extensions
- [ ] `api/live.ts`: one `EventSource('/api/v1/live-updates')` per tab, `hint` → `invalidateQueries(['linked-accounts'])` with background refetch; on `open` after an error → invalidate all; mounted once in `App.tsx`
- [ ] `messages.ts`: the `linking`, `accounts`, `banner` groups with every SCR-02/10/60 + banner string from `screens.md`, and one entry per new problem code
- [ ] Vitest: client calls + problem parsing, SSE hint → invalidation, reconnect → invalidate all, background header on hint refetches

## Edge cases

| Case | Behaviour |
|---|---|
| Stream drops and reconnects | Invalidate everything shown |
| Unknown hint name | Ignored |
| Problem code without a catalog entry | Shared failure routing (SCR-93) — every new code has an entry |

## Definition of Done

- [ ] Vitest for the clients and the live client passes
- [ ] `pnpm run check` clean (tsc, ESLint, Prettier)
- [ ] every Hard Rule inlined above still holds
