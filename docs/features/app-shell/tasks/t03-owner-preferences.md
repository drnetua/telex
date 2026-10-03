---
id: T3
title: "Read and change the Owner's theme and timezone in identity, with the save-if-unset write"
layer: "infra"
deps: ["T1", "T2"]
blocks: ["T5"]
acs: ["AC-179", "AC-183", "AC-184", "AC-186"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/OwnerPreferences.kt", "backend/app/src/main/kotlin/telex/identity/OwnerProfiles.kt", "backend/app/src/main/kotlin/telex/identity/internal/owner/Owners.kt", "backend/app/src/integrationTest/kotlin/telex/identity/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T3 — Read and change the Owner's theme and timezone in identity, with the save-if-unset write

## Place in the sequence

- **Blocked by:** T1 — Promote the staged owner-preferences migration, T2 — Add the Theme type and the known timezone list · **Blocks:** T5 — Serve preferences and the timezone list over `/api/v1` · **Wave:** 2 — needs the columns (T1) and the value rules (T2).
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to choose a light, dark or system theme once and have it on all my devices
> **So that** teleX is comfortable to read at any time of day
>
> — `spec.md §4, US-73, verbatim` · full text: [spec.md](../spec.md)

> **As an** Owner
> **I want** teleX to know my timezone and show dates and times in it
> **So that** times on every screen match my day, wherever my browser runs
>
> — `spec.md §4, US-74, verbatim` · full text: [spec.md](../spec.md)

This task is the one place the preference rules live: never-empty timezone, list-only zones, first save only while unset.

## Inlined context

> `identity` (core) gains three preference columns (theme, timezone, and whether the timezone is the UTC fallback), the preference rules (known timezone list, never empty, first save only while unset) and a typed read for other modules.
> `OwnerPreferences` — read (theme, time zone), change theme, change time zone, set time zone if not yet saved, timeZoneOf(ownerId) for other modules. `OwnerProfiles` — existing; Me gains theme + timeZone. `internal/owner/` — Owners repository gains the two columns (JdbcClient).
>
> — `sad.md §5, building blocks + Internal decomposition, abridged` · full text: [sad.md](../sad.md)

> Flow 7: save this timezone only if none is saved yet → keep it if it is on the known list, else use UTC and mark it as the fallback → set the timezone and fallback flag only where no timezone is saved → *still unset*: saved · *another device saved first*: nothing changed → return the saved timezone and whether it is the fallback.
> Flow 8: check it is not empty and is on the known list → *on the list*: update the Owner's timezone, clear the fallback flag · *empty*: refused, timezone required · *not on the list*: refused, unknown timezone.
>
> — `sad.md §6, Flow 7 steps 9–14 + Flow 8 steps 8–14, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** Every Owner-owned row carries `owner_id`, and every query filters on it. Preferences are read and written only for the Owner of the current session.
>
> — `sad.md §2 Conventions + spec.md §6.1 AuthZ, abridged`

> "Never cleared once set" (AC-186) is a state transition that a `CHECK` can't express, and the repo uses no triggers. It's enforced in `identity` code and covered by an integration test. Other modules (E19, E20) read the timezone through `OwnerPreferences.timeZoneOf(ownerId)`, never the table.
>
> — `data-model.md §Entities, owner, abridged` · full text: [data-model.md](../data-model.md)

**Fallback:** insufficient or contradicted by the code → read [data-model.md](../data-model.md), [sad.md](../sad.md) §6 Flows 7–8 and [adr/0005](../adr/0005-store-theme-and-timezone-as-columns-on-the-owner-row.md). Do not guess.

## Data delta

| Column | Type | Constraints | Change |
|---|---|---|---|
| `owner.theme` | `VARCHAR(6)` | NOT NULL, `owner_theme_ck` | read / written |
| `owner.time_zone` | `VARCHAR(64)` | NULL until first save | read / written, never back to NULL |
| `owner.time_zone_is_fallback` | `BOOLEAN` | NOT NULL, `owner_time_zone_fallback_ck` | read / written |

Access patterns (all by `owner_pkey`):
- `UPDATE owner SET theme = ? WHERE id = ?`
- `UPDATE owner SET time_zone = ?, time_zone_is_fallback = ? WHERE id = ? AND time_zone IS NULL` — zero rows means another device saved first; then read the saved value.
- `UPDATE owner SET time_zone = ?, time_zone_is_fallback = false WHERE id = ?`

Fixtures: `anOwner(...)` gains `theme = "system"`, `timeZone: String? = null`, `timeZoneIsFallback = false`; add `anOwnerWithTimeZone(timeZone = "Europe/Kyiv")` and `anOwnerOnUtcFallback()`.

— `data-model.md §Entities (access patterns) + §Test fixtures, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no API surface. `OwnerProfiles`'s `Me` gains `theme`, `timeZone` (nullable) and `timeZoneIsFallback`, which T5 serialises.

## Acceptance criteria

### AC-179 — happy

> **Given** a signed-in Owner on Profile and security with the light theme
> **When** they choose Dark
> **Then** every screen of teleX switches to the dark theme at once, without a reload, and the choice is saved to their account
>
> — `spec.md §5, AC-179, verbatim` · full text: [spec.md](../spec.md)

### AC-183 — happy

> **Given** an Owner who has no timezone saved yet
> **When** they open teleX after this change
> **Then** their timezone is taken from the device and saved to their account (UTC, with a hint on Profile and security to pick their own, when the device's timezone can't be read or isn't on the list), Profile and security shows it, a device in another timezone later doesn't change it, and the dates on that page (when a Sign-in Session or Passkey was last used) are shown in it
>
> — `spec.md §5, AC-183, verbatim` · full text: [spec.md](../spec.md)

### AC-184 — happy

> **Given** an Owner on Profile and security
> **When** they pick another timezone from the list (searchable by city or region)
> **Then** the new timezone is saved, and every date and time teleX shows them uses it from then on, on all their devices (devices already open pick it up the next time teleX is opened or reloaded there)
>
> — `spec.md §5, AC-184, verbatim` · full text: [spec.md](../spec.md)

### AC-186 — domain invariant

> **Given** an Owner with a saved timezone
> **When** they try to leave the timezone empty
> **Then** teleX doesn't allow it and keeps the current one ("an Owner always has exactly one timezone"); the only way to change it is to pick another from the list
>
> — `spec.md §5, AC-186, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `Owners` repository: read and write the three columns; conditional "only if unset" update returns rows affected — `identity/internal/owner/Owners.kt`
- [ ] `OwnerPreferences` (public, `identity` root): `of(ownerId)`, `changeTheme`, `changeTimeZone` (throws a `DomainProblem` with field code `time-zone-required` for null/blank, `unknown-time-zone` off the list), `saveDetectedTimeZone(ownerId, zone?)` (unknown/null → `UTC` + fallback; never refuses), `timeZoneOf(ownerId)` — `identity/OwnerPreferences.kt`
- [ ] `OwnerProfiles.Me` gains `theme`, `timeZone`, `timeZoneIsFallback` — `identity/OwnerProfiles.kt`
- [ ] Integration tests on Testcontainers: each access pattern; second `saveDetectedTimeZone` doesn't overwrite; change clears the fallback flag; empty/unknown refused and value kept — `src/integrationTest/kotlin/telex/identity/OwnerPreferencesIT.kt` + fixture builders

## Edge cases

| Case | Behaviour |
|---|---|
| Two tabs race the first save | One `UPDATE … AND time_zone IS NULL` wins; the other reads and returns the saved value |
| Detected zone `null`, blank or off the list | Saves `UTC`, `time_zone_is_fallback = true` |
| Detected zone sent after a zone is already saved | No write; returns the saved preferences |
| Change to `null` / `""` | Refused `time-zone-required`; stored value unchanged |
| Change to `Europe/Atlantis` | Refused `unknown-time-zone`; stored value unchanged |
| Change to a valid zone while on the UTC fallback | Saves it, clears `time_zone_is_fallback` |

## Definition of Done

- [ ] `OwnerPreferencesIT` covers every row of the edge-case table and passes
- [ ] `ModularityTest` green (`OwnerPreferences` is public API at the `identity` root)
- [ ] every Hard Rule inlined above still holds (all reads/writes by the session Owner's id)
- [ ] `./gradlew spotlessCheck detekt` clean
