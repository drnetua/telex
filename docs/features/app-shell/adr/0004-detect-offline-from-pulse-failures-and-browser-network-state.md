---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E06 app-shell-responsive"
---

# 0004 — Detect offline from pulse failures and the browser's network state

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk, easy-level assumptions ledger accepted by the user)

## Context

AC-176 asks for a Status Banner within 5 s when the device has no network ("You're offline") or teleX doesn't answer ("teleX isn't responding"), and for it to clear by itself within 5 s of recovery. It also narrows E01's AC-102: an action with no answer now shows the banner and keeps the screen, and the full page SCR-93 stays only for an action teleX answers with a failure. An Owner who is only reading makes no requests, so waiting for an action to fail can't meet 5 s.

## Decision drivers

- Spec §6: banner appears ≤ 5 s and clears ≤ 5 s, on both e2e profiles.
- AC-177: "Try again" while still down keeps the banner and clears nothing on screen.
- ADR-0002: a 3 s pulse already runs on every visible tab.
- Phones: no extra traffic beyond what's already there.

## Considered options

1. **Use the pulse as the heartbeat, plus the browser's network state.** The pulse waits at most 2 s for an answer. The browser's `offline` event, or `navigator.onLine` being false, means "You're offline". A network error, no answer in 2 s, or, inside the shell, any 502/503/504 (whatever its body) while the network is up means "teleX isn't responding". Outside the shell (before it mounts) a 502/503/504 still opens SCR-93. An ordinary call with no answer in 10 s or a network error feeds the same state instead of SCR-93.
2. **A separate ping request every 2 s,** independent of the data pulse.

## Decision outcome

**Chosen:** option 1 (in the walk the user picked "heartbeat in the live channel plus network state", and the live channel is the pulse). Worst case, a drop right after a successful pulse is noticed 3 s + 2 s = 5 s later, and the browser's `offline` event is usually instant. Option 2 doubles the background traffic for the same signal.

## Consequences

**Positive**
- Detection and recovery need no traffic beyond the pulse. While the banner shows, the pulse keeps running every 3 s, so the banner clears within about 3 s of recovery.
- One connectivity state (`online`, `offline`, `not-responding`) drives the Status Banner and TanStack Query's `onlineManager`. Paused queries resume and refetch on recovery, so the screen shows fresh data (AC-176) without per-page code. The pulse itself is exempt (`networkMode: 'always'`): it keeps polling every 3 s while other queries are paused, so it is what detects recovery.
- AC-102 narrows in one place, the fetch client's failure mapping. Answered failures still go to SCR-93.

**Negative**
- The 5 s budget is tight: 3 s interval + 2 s timeout. A slow but working teleX (an answer after 2 s) shows "teleX isn't responding" until the next pulse arrives in time.
- `navigator.onLine` can say "online" behind a captive portal. The pulse then fails and the shell reports "teleX isn't responding", which is acceptable wording for that case.
- Polling pauses in hidden tabs, so a hidden tab learns about a drop only once it becomes visible. The pulse fires immediately at that point.

**Neutral**
- A pulse answered with `unauthenticated` or `session-ended` routes the tab to SCR-01 or SCR-92 straight away. That is stricter than AC-173's "next action" and is intended: no shell after the session stops.

## Links

- Spec: [[../spec.md]] AC-176, AC-177, AC-178, §6
- SAD: [[../sad.md]] §4, §8
- Related ADR: [[0002-poll-one-background-pulse-every-3-seconds-for-live-signals]], [[0006-extend-the-shell-through-client-section-and-server-condition-registries]]
