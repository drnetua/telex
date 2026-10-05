---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E02 telegram-link"
---

# 0005 — Push live state to the SPA as SSE invalidation hints, one stream per browser tab

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

The Accounts page has to show changes while it is open: sync progress (AC-116), the chat count within a minute (AC-121), and "Reconnecting" or "Session lost" (AC-122). Every signed-in screen has to show the "account disconnected" Status Banner (AC-122). `ux-flows.md` left the mechanism to design. teleX has no server-to-browser channel yet. `architecture-map.md` plans "one SSE client feeding query invalidation", and E04 (new messages) and E06 (the live Inbox counter) will need the same channel.

## Decision drivers

- Spec §6: changes show within 60 s, and "Session lost" shows ≤ 5 min after the session ends.
- One mechanism for E02, E04 and E06 instead of one per epic.
- Authorization stays in one place: the REST endpoints already scope everything to the Owner (AC-03).
- A single app instance (§7).

## Considered options

1. **Server-Sent Events (SSE) with invalidation hints.** `web` serves one `text/event-stream` per tab for the Owner of the session cookie. It carries only small named hints such as `linked-accounts`, with no data. The SPA's single SSE client turns each hint into a TanStack Query `invalidateQueries`, which refetches through the normal REST endpoints.
2. **Polling.** The Accounts page and the banner refetch every 5–10 s with `refetchInterval`, marked as background requests (E01 ADR-0005).

## Decision outcome

**Chosen:** option 1. Changes reach the page in about a second, and the stream carries no Owner data, so it adds no new authorization surface. A hint only tells the SPA what to refetch. `web` keeps an in-memory registry of open streams per `OwnerId`, which is enough for one instance. It listens after commit to `messaging`'s Linked Account events and to the throttled sync-progress event, then sends the matching hints, at most one per hint name per second per Owner. A comment heartbeat every 25 s keeps Cloudflare and other proxies from closing an idle stream. Responses carry `X-Accel-Buffering: no` and `Cache-Control: no-store`. The stream is authenticated by the session cookie like any request, and it counts as a background request: it never bumps Sign-in Session activity (E01 ADR-0005, decided by path, because `EventSource` can't set headers). When the session ends, the stream closes. After any reconnect the SPA invalidates everything it shows, so a missed hint never leaves stale state.

## Consequences

**Positive**
- E04 and E06 add hint names, not a new channel.
- No Owner data passes outside the REST endpoints and their checks.

**Negative**
- One long-lived HTTP connection per tab. With virtual threads this is cheap, but a second app instance would need a shared fan-out (§7).
- E06's one background pulse (app-shell ADR-0002) shipped first, so the SPA has two live channels (see the amendment).

**Neutral**
- Hints are not durable. That is acceptable, because the state lives in REST and is always refetched after a reconnect.

## Links

- Spec: [[../spec.md]] AC-116, AC-121, AC-122, §6
- SAD: [[../sad.md]] §4, §5, §8
- Related ADR: app-shell [[../../app-shell/adr/0002-poll-one-background-pulse-every-3-seconds-for-live-signals]], [[../../app-shell/adr/0006-extend-the-shell-through-client-section-and-server-condition-registries]]
- Related ADR: platform-skeleton [[../../platform-skeleton/adr/0005-count-session-activity-only-from-requests-the-spa-does-not-mark-as-background]]

## Amendment 2026-10-04 (E02 telegram-link)

Status stays Accepted. The Context said the Status Banner needed this stream and that E06 would build on it. E06 `app-shell` landed first and chose one background pulse every 3 s as the single live channel for shell signals (app-shell ADR-0002), with server-reported banner conditions (app-shell ADR-0006). The split as built:

- **SSE hints (this ADR)** carry `linked-accounts` invalidation only. They refresh the Accounts list, the SCR-10 lines and the sync progress (AC-116, AC-121) within about a second.
- **The pulse** decides the Status Banner condition (AC-122): `SessionLostConditions` reports `account-disconnected` while an account is Session lost, and the SPA fetches the list only for the banner's name and action.
- The "E06 must agree to build on this stream" consequence is dropped: E06 did not, and E04 and E06 are not bound to add hint names here. The decision to use SSE for Linked Account state stands, because the Accounts page needs sub-second progress that a 3 s pulse does not give.
- Cost: two channels while E02 is open. Either one failing leaves the other working: the banner still comes within one pulse, and the Accounts page still refetches on reconnect.
