---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "S"
ticket: "E10 model-profiles"
---

# 0002 — Keep Model Profiles in the `agents` core module and `llm` a thin provider ACL

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

The architecture map gives `llm` "OpenRouter, Model Profiles, cost, Budget". But `llm` is an integration module whose `package-info.java` allows only `shared`. `web` may not depend on it, and `OwnerId` lives in `identity`. Custom Model Profiles and the Owner's default profile are Owner-owned data that the Models page reads and writes. So putting them in `llm` would mean changing the module boundaries. E09 (agents pick a profile) and E14 (runs call a profile) will reference whatever module owns profiles.

## Decision drivers

- Architecture rule: integration modules are reached only through ports from core modules, and depend on `shared` only (`docs/architecture-map.md` §Module inventory).
- Every Owner-owned row carries `owner_id` and every query filters on it (spec §6.1, AC-222).
- Tech spec glossary: an Agent is "instruction + Model Profile + Tools + …", so profiles sit next to agents.
- S-sized feature: no boundary rework that ripples through E01 code.

## Considered options

1. **Profiles in `agents`, `llm` a thin ACL.** `llm` owns the Model Catalog (fetch, snapshot, capabilities, prices) and the model call over an ordered list of model ids. `agents` owns Model Profiles, the default profile, slot rules, profile resolution, price estimates and call records, and exposes the catalog to `web` in its own types, so `llm` types never reach `web`.
2. **A new core module `models`** between `web` and `llm`, which `agents` would later depend on.
3. **Everything in `llm`.** This moves `OwnerId` to `shared` and allows `web` → `llm`.

## Decision outcome

**Chosen:** option 1. It needs no change to any `allowedDependencies` (`web` → `agents` and `agents` → `llm` are already allowed). It keeps `llm` free of Owner data, as the ACL rule intends, and it puts profiles where E09 and E14 will use them.

## Consequences

**Positive**
- No Modulith boundary changes. `ModularityTest` stays green without touching other modules.
- `llm` stays provider-shaped: it knows models and calls, not Owners. A BYOK or second-provider adapter (E27) changes only `llm`.

**Negative**
- `agents` will grow large once E09 and E14 land in it. Model profiles are a separate sub-package (`agents/internal/profile`) so they can be split out later.

**Neutral**
- Splitting profiles into their own module later (option 2) means moving a package plus its tables. That takes a few days and is mechanical.
- The architecture map's `llm` row ("Model Profiles, cost, Budget") must be updated to "Model Catalog, model calls" on the next `survey`.

## Links

- Spec: [[../spec.md]] US-13, US-81, AC-222
- SAD: [[../sad.md]] §4, §5
- Related ADR: [[0003-client-side-fallback-loop-in-llm]], [[0004-call-records-in-agents-table]], [[0005-system-profiles-in-configuration-by-key]]
