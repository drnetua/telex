---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E06 app-shell-responsive"
---

# 0006 — Extend the shell through a client section registry and server-reported Status Banner conditions

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

Later epics replace their "Coming soon" page (E04, E09, E14, E22, E29) and add Status Banner conditions: account disconnected (E02), bot blocked (E19), consent needed, budget exhausted, all assistants paused, triage deferred. Spec §7 says none of them may change the shell beyond that. AC-178 fixes one importance order and "N more". Some conditions are known only to integration modules (`telegram`, `bot`), which may depend on `shared` only.

## Decision drivers

- Spec §7 KPI "Shell changes needed by later epics" = 0.
- AC-178: a banner lives exactly as long as its cause, the most important one shows, and the rest are listed under "N more".
- ADR-0002: the pulse is the one live channel.
- Module rules: integration modules depend on `shared` only, and `web` doesn't depend on integration modules.

## Considered options

1. **Client section registry plus server-reported conditions.** Sections are one typed list in the SPA (`id`, path, label, icon, phone placement, page or Coming soon). Conditions come from `StatusConditionSource` implementations, an interface in `shared` with no beans, so core and integration modules can both implement it. `web` collects the active condition codes into the pulse, and the SPA maps each code to text, action and importance. Offline and not-responding stay client-side.
2. **Client registries for both.** Each condition is an SPA entry with its own data hook that reads its epic's own query.

## Decision outcome

**Chosen:** option 1. Server-side conditions go live within one pulse with no new request, and the truth stays where the cause is known, including in integration modules. Option 2 needs a request, or a pulse field, per condition anyway, plus a separate API for every integration-side condition first.

## Consequences

**Positive**
- A later epic adds a condition by implementing `StatusConditionSource` in its module and adding one entry (code, text, action, importance) to the SPA's condition catalog. The shell's banner code doesn't change.
- A later epic adds its section page by swapping its registry entry from Coming soon to its page. Navigation, phone bar and "More" are generated from the registry.
- The importance order lives in one place, the SPA condition catalog, with offline and not-responding first. The spec §8 order is a list there.

**Negative**
- E06 ships the condition mechanism with no real server source. AC-178 ("N more") is tested with a fixture source under the `e2e` profile (sad §11).
- A condition code is a contract between the server and the SPA. An unknown code is ignored by the SPA and logged, so a server that ships first can't break the banner.

**Neutral**
- `shared` stays a types-only kernel: the interface carries no Spring annotations. Beans implementing it live in their modules, and `web` receives them as a list.

## Links

- Spec: [[../spec.md]] AC-171, AC-178, §7, §8
- SAD: [[../sad.md]] §4, §5
- Related ADR: [[0001-shell-scope-moves-offline-banner-coming-soon-panel]], [[0002-poll-one-background-pulse-every-3-seconds-for-live-signals]], [[0004-detect-offline-from-pulse-failures-and-browser-network-state]]
