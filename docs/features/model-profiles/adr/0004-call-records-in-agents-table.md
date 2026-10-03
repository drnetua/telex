---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "S"
ticket: "E10 model-profiles"
---

# 0004 — Store content-free model call records in an `agents`-owned table, written in the call path

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

AC-229 requires each model call made through a profile to be recorded without any message content: the time, Owner, profile, slot, every model tried with its outcome, the model that answered and whether a fallback happened. "Run details and the fallback KPI read from one place." E14's run details (in `agents`) and the "Calls saved by fallback" KPI (spec §7) will both build on this record. The `audit` module is planned as the append-only log of agent and Owner actions, but it is still empty.

## Decision drivers

- AC-229: one place to read from, and never any message content (spec §6.1).
- Spec §7 KPI "Calls saved by fallback" is computed from these records.
- ADR-0002: Owner and profile context exist only in `agents`, so `llm` can't write the record.

## Considered options

1. **Own append-only table in `agents`.** The profile call service writes one `model_call` row (with its attempts) right after `llm` returns or fails, then publishes a `ModelCallFinished` event for anyone else.
2. **The `audit` module, through an event.** The caller publishes an event, and `audit` stores it asynchronously through the Modulith event publication registry.

## Decision outcome

**Chosen:** option 1. It puts the record in the same module as the future Runs, so run details and the KPI read it without crossing modules. It is written synchronously, so it is visible as soon as the call returns. With option 2, E10 would have to design `audit`'s model first, and the records would arrive eventually rather than immediately.

## Consequences

**Positive**
- The KPI is one query over one table, and run details (E14) join within `agents`.
- The `ModelCallFinished` event still lets `audit`, or the E27 budgets, subscribe later without a schema change here.

**Negative**
- The table grows with every model call. A retention policy is not part of E10 (§11).
- Append-only is a code discipline: the repository exposes insert and read only, because `audit`'s guarantees don't apply.

**Neutral**
- A record write failure must not turn a successful answer into an error. The call service logs it and returns the answer (§8).

## Links

- Spec: [[../spec.md]] AC-229, §7 KPIs
- SAD: [[../sad.md]] §5, §8
- Related ADR: [[0002-profiles-in-agents-llm-thin-acl]], [[0003-client-side-fallback-loop-in-llm]]
