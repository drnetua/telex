---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E06 app-shell-responsive"
---

# 0003 — Aggregate the Inbox count from sources in a new `inbox` module

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

The Inbox counter shows how many items wait for the Owner (CONTEXT "Inbox"). E06 adds no kind of item. Items arrive later from several modules, each with its own lifecycle: Notes (E11, `agents`), cases to check (E13, `triage`), drafts to approve (E18, `tasks`), tasks due today (E22, `tasks`), blocked messages (E24, `tools`). The pinned bot counter (E19) needs the same number. The shell must not change when those epics add their items (spec §7 KPI).

## Decision drivers

- Spec §7 KPI: no later UI epic changes the shell beyond its own page or banner.
- AC-174, AC-175: the count is live and counts only the current Owner's items.
- Architecture rule: no cross-module table access. Each module owns its data.
- AC-174 has to be testable in E06 with prepared items, before any producer exists.

## Considered options

1. **A new core module `inbox` that sums counts from sources.** Its public API holds an `InboxSource` interface (`countWaiting(ownerId)`), which producer modules implement, and an `Inbox.countWaiting(ownerId)` that sums them. Whether an item waits stays in the module that owns the item.
2. **A new `inbox` module with one `inbox_item` table that every producer writes to.** Producers call `open`/`resolve`, and the count is one query.
3. **The same sources, but with the API in the existing `tasks` module.**

## Decision outcome

**Chosen:** option 1. It keeps one owner for the "is it still waiting" state. Option 2 duplicates that state in a second table that can drift from the producer's own record, and it adds a table nobody fills until E11. Option 3 would force `agents`, `triage` and `tools` to depend on `tasks` for something unrelated to tasks, which blurs module boundaries.

## Consequences

**Positive**
- E06 adds no table. A later epic plugs in by implementing `InboxSource` in its own module.
- AC-175 holds by construction: every source is called with the current Owner's id and filters by `owner_id`.
- The Inbox screen's tabs (SCR-10) can follow the same source split later.

**Accepted deviation from foundation ADR-0003 (review D3)**
- `InboxSource.countWaiting` takes a raw `UUID` owner id, not a typed `OwnerId`. `OwnerId` lives in `identity`, which `inbox` and the source-implementing modules can't reach, and moving it into `shared` is out of scope. Implementations must still filter on that id only (AC-175).

**Negative**
- The count costs one query per source per pulse (ADR-0002). Each source must be a cheap, indexed count.
- Producers in integration modules can't implement a core module's interface. Today none is planned, and if one appears it publishes an event that a core module turns into a source.
- In E06 there is no real source. The e2e for AC-174 uses a fixture source under an `e2e` Spring profile (sad §11).

**Neutral**
- `ModularityTest` gains a 15th module: `inbox` depends on `shared` only, `web` gains `inbox` in its allowed dependencies, and producers add `inbox`.
- If the Inbox screen later needs one sorted list across kinds, a read model can be added behind the same API without changing producers' sources.

## Links

- Spec: [[../spec.md]] AC-174, AC-175, §7
- SAD: [[../sad.md]] §4, §5
- Related ADR: [[0002-poll-one-background-pulse-every-3-seconds-for-live-signals]]
