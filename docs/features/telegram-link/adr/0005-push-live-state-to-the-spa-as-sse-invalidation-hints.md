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
- E06 (`app-shell`, designed in parallel) must agree to build its live counter on this stream (§11).

**Neutral**
- Hints are not durable. That is acceptable, because the state lives in REST and is always refetched after a reconnect.

## Links

- Spec: [[../spec.md]] AC-116, AC-121, AC-122, §6
- SAD: [[../sad.md]] §4, §5, §8
- Related ADR: platform-skeleton [[../../platform-skeleton/adr/0005-count-session-activity-only-from-requests-the-spa-does-not-mark-as-background]]
