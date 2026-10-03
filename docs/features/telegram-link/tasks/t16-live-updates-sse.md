---
id: T16
title: "Serve the live-update SSE stream of invalidation hints per Owner"
layer: "ports"
deps: ["T7"]
blocks: ["T24"]
acs: ["AC-116", "AC-121", "AC-122"]
files_hint: ["backend/app/src/main/kotlin/telex/web/live/", "backend/app/src/main/kotlin/telex/web/security/", "backend/app/src/integrationTest/kotlin/telex/web/LiveUpdatesIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T16 — Serve the live-update SSE stream of invalidation hints per Owner

## Place in the sequence

- **Blocked by:** T7 — Build the LinkedAccount aggregate: rules, states, masked phone, repository, events and the account limit · **Blocks:** T24 — Prove nothing is left behind or leaked: unlink dump, restart delivery, registry and log scans · **Wave:** 3 — needs only the event types (T7) — parallel to everything else in `messaging`.
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to see whether each Linked Account is connected (and whether it is still syncing its chats), is reconnecting after Telegram was unreachable, or has lost its session, and to sign in to it again when it has
> **So that** I know when teleX can work with an account and fix it without starting over
>
> — `spec.md §4, US-51, verbatim` · full text: [spec.md](../spec.md)

This task makes state changes reach open pages in about a second, without moving any Owner data outside the REST endpoints.

## Inlined context

> `web` keeps an in-memory registry of open streams per `OwnerId`, which is enough for one instance. It listens after commit to `messaging`'s Linked Account events and to the throttled sync-progress event, then sends the matching hints, at most one per hint name per second per Owner. A comment heartbeat every 25 s keeps Cloudflare and other proxies from closing an idle stream. Responses carry `X-Accel-Buffering: no` and `Cache-Control: no-store`. The stream is authenticated by the session cookie like any request, and it counts as a background request: it never bumps Sign-in Session activity (E01 ADR-0005, decided by path, because `EventSource` can't set headers). When the session ends, the stream closes.
>
> — `adr/0005 §Decision outcome, abridged` · full text: [0005-push-live-state-to-the-spa-as-sse-invalidation-hints.md](../adr/0005-push-live-state-to-the-spa-as-sse-invalidation-hints.md)

> Listener rule: skip if this `linkedAccountId` was already handled, because a restart can deliver it again. Each listener is idempotent by `linkedAccountId`. A hint is harmless to repeat.
>
> — `contracts/events.md §AccountUnlinked + §Idempotency, abridged` · full text: [events.md](../contracts/events.md)

> **Hard rule:** The stream carries invalidation hints only, never Owner data.
>
> — `contracts/openapi.yaml, openLiveUpdates, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes. (`event_publication` rows for the four messaging events are completed by these listeners.)

## API contract

- `openLiveUpdates` `GET /api/v1/live-updates` → `200 text/event-stream`, headers `Cache-Control: no-store`, `X-Accel-Buffering: no`; body `event: hint\ndata: linked-accounts\n\n` and `: keep-alive` comments every 25 s · `401`.
- `LiveHint` enum: `linked-accounts` (E04/E06 add values).

— `contracts/openapi.yaml, operationId openLiveUpdates + schema LiveHint, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

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

- [ ] `web/live/LiveUpdatesController` returning an `SseEmitter` (no timeout) registered in `web/live/EmitterRegistry` keyed by `OwnerId` + Sign-in Session
- [ ] `@ApplicationModuleListener`s for `AccountLinked`, `AccountUnlinked`, `LinkedAccountStateChanged`, `LinkedAccountSyncProgressed` → throttled `linked-accounts` hint per Owner (≤ 1/s, trailing send)
- [ ] 25 s heartbeat job; on each beat check `SignInSessions.isLive` and complete the emitters of ended sessions
- [ ] `web/security/`: treat `/api/v1/live-updates` as background by path (no activity bump), as E01 ADR-0005 does for the header
- [ ] `LiveUpdatesIT`: hint after each event type to the right Owner only, throttling, headers, heartbeat, no activity bump, closes after sign-out

## Edge cases

| Case | Behaviour |
|---|---|
| Owner with no open tab | Hint dropped; publication still completed |
| Another Owner's account event | No hint on this Owner's stream |
| `AccountUnlinked` redelivered after restart | Repeat hint — harmless |
| Burst of 50 sync progress events | ≤ 1 hint per second, the last one always sent |

## Definition of Done

- [ ] `LiveUpdatesIT` passes
- [ ] stream bytes contain only hint names and comments
- [ ] detekt + ktlint clean; `ModularityTest` green
- [ ] every Hard Rule inlined above still holds
