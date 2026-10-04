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

**Accepted deviation from foundation ADR-0003 (review D3)**
- `StatusConditionSource.activeConditions` takes a raw `UUID` owner id, not a typed `OwnerId`. `OwnerId` lives in `identity`, which `shared` can't depend on, and moving it into `shared` is out of scope.

**Negative**
- E06 ships the condition mechanism with no real server source. AC-178 ("N more") is tested with a fixture source under the `e2e` profile (sad §11).
- A condition code is a contract between the server and the SPA. An unknown code is ignored by the SPA and logged, so a server that ships first can't break the banner.

**Neutral**
- `shared` stays a types-only kernel: the interface carries no Spring annotations. Beans implementing it live in their modules, and `web` receives them as a list.

## Amendment 2026-10-04 (E02 telegram-link)

Status stays Accepted. E02's `account-disconnected` condition needed live text and an action that the server-reported code cannot carry (the Session lost account's name, a per-account "Sign in again"). The shell was extended once, generically, instead of adding a request or a pulse field:

- **The pulse still decides whether a condition is active.** `SessionLostConditions` (`backend/app/src/main/kotlin/telex/messaging/internal/account/SessionLostConditions.kt:9`) implements `StatusConditionSource`, and the pulse reports `account-disconnected` while an account is `session_lost`. Nothing about the order, "N more" or the code contract changes.
- **A catalog entry may carry live data.** `useConditionLives(codes)` (`frontend/src/shell/conditions.ts:84`) is called once by `StatusBanner` and returns a `ConditionLive` per code: `message` and `action` (both fall back to the catalog entry), `notice` (a node rendered with the banner, for example a refusal Toast) and `inactive` (loaded data contradicts the pulse, so the banner drops the condition, `StatusBanner.tsx:95`). A later epic adds its hook to that one function. This replaces "the shell's banner code doesn't change" for the live part only: the banner code stays free of epic specifics.
- **A `button` action kind** (`conditions.ts:12`) next to `retry` and `link`, for an action that runs code instead of navigating.
- **Mutation callbacks live at hook level, not in the line.** The Sign in again handlers and the refusal Toast sit in `useAccountDisconnected` (`frontend/src/shell/accountDisconnected.tsx:16-35`), above `ConditionLine`, so they survive the line being outranked by connectivity or dropped by a refetch. The notices render in the banner's `role="status"` wrapper (`StatusBanner.tsx:97-102`).
- **The data hook is gated by the pulse.** The Linked Accounts list is fetched only while the pulse reports the condition (`accountDisconnected.tsx:17`, `conditions.ts:86`), and a list being refetched is not trusted to hide the banner (`accountDisconnected.tsx:45`), so the false generic text never shows after Sign in again or the last unlink.
- **The shell imports `api/linking` and `api/linkedAccounts`.** Accepted: the dependency points from the shell hook into the feature's API layer, one way, inside the SPA.

The reconsidered option 2 (per-condition client hooks) stays rejected as the way to decide a condition: the server remains the source of truth for "active". The hook only supplies text and action. The spec §7 KPI "Shell changes needed by later epics" was spent once, by E02, on this generic extension; a later epic that only adds a catalog entry and a `useConditionLives` line is within it.

## Links

- Spec: [[../spec.md]] AC-171, AC-178, §7, §8
- SAD: [[../sad.md]] §4, §5
- Related ADR: [[0001-shell-scope-moves-offline-banner-coming-soon-panel]], [[0002-poll-one-background-pulse-every-3-seconds-for-live-signals]], [[0004-detect-offline-from-pulse-failures-and-browser-network-state]]
