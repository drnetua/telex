---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-02"
feature_size: "M"
ticket: "E01 platform-skeleton"
---

# 0005 — Count session activity only from requests the SPA does not mark as background

- **Status:** Accepted
- **Date:** 2026-10-02
- **Deciders:** Anton Husiev, Claude (design, easy-level assumptions ledger — accepted by the user)

## Context

A Sign-in Session ends after 30 days without activity. Activity means a page the Owner opens or an action they take, and background refreshes of an open tab don't count (AC-96, CONTEXT "Sign-in Session"). The server can't tell these apart by itself. TanStack Query refetches on window focus and on intervals, and the future SSE stream (from E02) keeps a tab chatty. Without a signal, a forgotten tab would keep a session alive forever, short of the 90-day cap.

## Decision drivers

- AC-96: the 30-day idle rule, with "background refreshes of an open tab don't count".
- Quality goal 1 (sad §1): a lost device's session must eventually die on its own.
- Keep the write load low: one session update per request would double the writes for chatty pages.

## Considered options

1. **SPA marks background requests.** The fetch client sends `X-Telex-Background: 1` on every request TanStack Query starts by itself (refetch on focus, interval, reconnect) and on the future SSE stream. The server bumps `last_activity_at` only for unmarked requests, at most once per minute per session.
2. **SPA sends explicit activity pings.** It posts "activity" on route changes and user actions, and the server ignores activity on all other requests.
3. **Server-side heuristics.** Count only non-GET requests and full page loads as activity.

## Decision outcome

**Chosen:** option 1. The default, an unmarked request, counts as activity, so a forgotten marker errs toward keeping a session alive rather than signing someone out unexpectedly. The marker is set in one place, the fetch client's TanStack integration, not at each call site. Option 2 needs a ping wired into every navigation and action, and it drifts as pages are added. Option 3 misses the Owner reading pages (GETs) and would sign out a read-only user after 30 days.

## Consequences

**Positive**
- AC-96 is testable end to end: background requests over 31 days leave the session idle, and one page open resets it.
- At most one activity write per session per minute.

**Negative**
- A frontend/backend contract: the header name is part of the API (`/sdd:api` documents it). A non-SPA client that never sends it counts all its requests as activity, which is acceptable.
- The rule depends on the client telling the truth. A malicious client can keep its own session alive, but never past the 90-day cap, and it already holds the session anyway.

**Neutral**
- E02's SSE stream must send the marker (or be treated as background server-side). This is noted for that epic's design.

## Links

- Spec: [[../spec.md]] AC-96
- SAD: [[../sad.md]] §8
- Related ADR: [[0001-keep-sign-in-sessions-in-an-identity-owned-table-behind-an-opaque-cookie]]
