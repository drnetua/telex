---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E06 app-shell-responsive"
---

# 0002 — Poll one background pulse every 3 seconds for live signals

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

The Inbox counter on an already open tab must change within 5 s of an item landing or being resolved (AC-174, spec §6 "Inbox counter freshness"). Until now teleX has had no way to push anything from the server to the browser: the SPA only asks. Whatever carries the counter also becomes the channel that later epics use for their live signals (E02 account status, Status Banner conditions per ADR-0006), so the choice reaches beyond this feature.

## Decision drivers

- Spec §6: Inbox counter freshness ≤ 5 s, offline banner appears and clears ≤ 5 s.
- Quality goal 3 (sad §1): later epics add live signals without changing the shell.
- Platform-skeleton ADR-0005: background traffic must not keep a Sign-in Session alive.
- One app process, one developer: the fewest moving parts that meet the numbers.

## Considered options

1. **One SSE stream per tab.** A long-lived server-sent-events connection from `web`, carrying small "this changed" signals.
2. **Poll one pulse request every 3 s.** Each visible tab sends `GET /api/v1/pulse`, marked background. The answer carries the Inbox count and the active Status Banner conditions, and later epics add fields to it.
3. **A WebSocket.** A two-way connection with its own handshake authentication.

## Decision outcome

**Chosen:** option 2, the user's choice in the design walk. It meets the 5 s targets with plain request and response, passes any proxy without special settings, reuses the existing session, CSRF and background-marker handling unchanged, and doubles as the heartbeat for offline detection (ADR-0004). Option 1 updates faster and sends nothing while idle, but needs held connections, server-side session revalidation and an unbuffered proxy. Option 3 adds two-way messaging that teleX doesn't need, since commands go through REST.

## Consequences

**Positive**
- One small request per visible tab every 3 s replaces any per-feature polling. Later epics add a field to the pulse instead of opening another channel.
- The pulse is also the liveness probe, so offline detection needs no extra traffic.
- No held connections, no proxy buffering concerns, no new server infrastructure.

**Negative**
- About 20 requests a minute per visible tab even when nothing changes. Polling pauses while the tab is hidden and fires at once when it becomes visible again, which limits the cost on phones.
- Updates arrive up to 3 s late. That's within the spec's 5 s, but signals that need to feel instant (a new chat message in E04, run progress in E14) may want a push channel later.
- Every pulse runs the Inbox and condition sources for the Owner, so each source must stay a cheap, indexed count.

**Neutral**
- Moving to SSE later is possible: the pulse answer is a plain document of signals, and the same document can be sent as an event. The SPA's single place that applies a pulse would change, not the features that read it.

## Links

- Spec: [[../spec.md]] AC-174, AC-176, §6
- SAD: [[../sad.md]] §4
- Related ADR: [[0003-aggregate-the-inbox-count-from-sources-in-a-new-inbox-module]], [[0004-detect-offline-from-pulse-failures-and-browser-network-state]], [[0006-extend-the-shell-through-client-section-and-server-condition-registries]]
