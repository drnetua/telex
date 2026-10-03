---
status: Accepted
owner: "Anton Husiev (PM)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E02 telegram-link"
---

# 0001 — Move "pause agents on unlink" from E02 to E09 and E20; E02 only announces the unlink

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (specify interview)

## Context

Epic E02 feature 5 says that unlinking a Telegram account signs out of Telegram, destroys the session and pauses the linked agents. Its AC-05 says that unlinking an account with 3 agents marks all three "paused: no account" and cancels their scheduled runs. Agents don't exist until E09 (agent-builder), and scheduled runs don't exist until E20 (schedules-digest). The shared Definition of Done in `docs/docs/02-epics.md` forbids stubs, and it requires an explicit spec change, an ADR and a new epic before a feature leaves an epic's scope.

## Decision drivers

- Shared DoD: no stubs, and every AC is covered by a green automated test.
- Roadmap order: E02 is in wave 3, while E09 and E20 come in waves 7 and 10.
- The unlink must still reach later features, including unlinks that happened before those features shipped.

## Considered options

1. **Announce now, pause later.** E02 announces every unlink to the rest of teleX, and the announcement survives a restart (spec AC-112, §6). The agent pause moves to E09 and the cancellation of scheduled runs moves to E20, both built on that announcement.
2. **Keep AC-05 in E02.** E02 then can't close until E09 ships, or it needs a stand-in "agent", which breaks the DoD.
3. **Drop AC-05.** E09 designs its own reaction from scratch, and risks missing unlinks that happened before it shipped.

## Decision outcome

**Chosen:** option 1. AC-05 keeps its id and moves to E09 (US-15) with a note for E20. E02 feature 5 now reads "sign out of Telegram + destroy the session + announce the unlink". No new epic is needed, because E09 and E20 already exist and take over the work. `02-epics.md` is patched in the same commit as the telegram-link spec.

## Consequences

- Good: E02 closes on its own value, with every AC testable now.
- Good: E09 and E20 get a ready-made signal and a recorded rule to build on.
- Bad: E09's scope grows by one cross-context AC, and E20's by one feature line.
- Watch: an account unlinked before E09 ships has no agents, so nothing has to be replayed. E09 only reacts to unlinks that happen after it ships.
