---
status: Accepted
owner: "Anton Husiev (PM)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E06 app-shell-responsive"
---

# 0001 — E06 shows "Coming soon" sections, ships "offline" as the first Status Banner, and moves the adaptive panel to E14

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (specify interview)

## Context

Epic E06 (`docs/docs/02-epics.md`) asks for a shell with navigation, a Status Banner mechanism whose first real scenario is "account disconnected", and AC-07b, under which side panels open full screen on a phone. Three facts collide with that card. First, only the Inbox and Profile and security exist when E06 starts, while the app map has seven sections. Second, Linked Accounts arrive with E02, which runs in the same wave 3. Third, no E06 screen has a side panel; the first one is SCR-41 run details in E14. The shared Definition of Done forbids stubs, and it lets a feature leave an epic only through a spec change plus an ADR.

## Decision drivers

- Every E06 AC is testable when E06 ships, without waiting for a parallel epic.
- The Owner sees the whole map of teleX from day one (the user's choice in the specify interview).
- Later epics learn about the work they inherit from the epics doc, not only from this spec.

## Considered options

1. **Show all seven sections with "Coming soon" pages, make "offline" the first banner, and move the panel to E14.** E06 stays independent of E02 and E14, and each scope move is written into the receiving epic's card.
2. **Show only built sections, wait for E02 for "account disconnected", and build the panel without a screen.** This follows the card literally, but it adds a 2 → 6 dependency, ties E06 to the Telegram-library risk (roadmap D1), and leaves a component that no end-to-end test can reach.
3. **Keep the card literally with stand-ins.** A fake disconnected account and a demo panel break the "no stubs" rule in a hidden way.

## Decision outcome

**Chosen:** option 1.

- The seven sections appear in E06. Each unbuilt one opens a visible "Coming soon" page, a deliberate and tested state. The owning epic (E04 Chats, E09 Assistants, E14 Runs, E22 Tasks, E29 Overview) replaces it as part of its own scope.
- E06 feature 2 ships the Status Banner mechanism with "offline" as its first real condition. E02 adds "Telegram account disconnected" on top of that mechanism, as its own AC.
- The adaptive panel (C-05) and the "side panels open full screen on a phone" half of AC-07b move to E14, with SCR-41 as their first screen. AC-07b in E06 keeps "no sideways scrolling at phone width".
- `02-epics.md` is patched for E06, E02 and E14 in the same commit as the app-shell spec.

## Consequences

- Good: E06 closes on its own value, with every AC testable now and no dependency on E02.
- Good: the offline banner covers a case that matters most on phones and needs no other epic.
- Bad: until each section's epic ships, the Owner can open pages that hold nothing yet. This is a visible exception to "no stubs", accepted on purpose.
- Bad: E02 and E14 each gain one piece of scope, and E04, E09, E14, E22 and E29 each have to replace their "Coming soon" page.
- Watch: at each UI epic's `/sdd:review`, check that its "Coming soon" page is gone and that it didn't change the shell beyond that (spec §7 KPI).
