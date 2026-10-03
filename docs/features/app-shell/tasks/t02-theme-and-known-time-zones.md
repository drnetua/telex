---
id: T2
title: "Add the Theme type and the known timezone list to identity"
layer: "domain"
deps: []
blocks: ["T3"]
acs: ["AC-184", "AC-186"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/Theme.kt", "backend/app/src/main/kotlin/telex/identity/TimeZones.kt", "backend/app/src/test/kotlin/telex/identity/TimeZonesTest.kt", "backend/app/src/test/kotlin/telex/identity/ThemeTest.kt"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "S"
status: "todo"
---

# T2 — Add the Theme type and the known timezone list to identity

## Place in the sequence

- **Blocked by:** — · **Blocks:** T3 — Read and change the Owner's theme and timezone in `identity` · **Wave:** 1 — pure domain, no schema or Spring dependency; runs in parallel with T1.
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** teleX to know my timezone and show dates and times in it
> **So that** times on every screen match my day, wherever my browser runs
>
> — `spec.md §4, US-74, verbatim` · full text: [spec.md](../spec.md)

This task defines the two value rules the preferences depend on: what a theme is, and which timezones exist.

## Inlined context

> `identity/Theme` — enum light | dark | system.
> `identity/TimeZones` — the known list (ZoneId ids in Area/City form + UTC), isKnown(id).
>
> — `sad.md §5, Internal decomposition (identity), abridged` · full text: [sad.md](../sad.md)

> **Known timezone list:** `ZoneId.getAvailableZoneIds()` limited to `Area/City` names plus `UTC`, served by `GET /api/v1/time-zones`. The SPA searches it by city or region, and the server rejects anything else.
>
> — `sad.md §8, Known timezone list, verbatim` · full text: [sad.md](../sad.md)

> `TimeZoneId`: `minLength: 1`, `maxLength: 64` — an IANA id in `Area/City` form, or `UTC`. The list is "sorted by id and returned whole".
>
> — `contracts/openapi.yaml, schemas TimeZoneId + operationId listTimeZones, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

> A tampered timezone value: only timezones from the known list are accepted. Anything else is refused and the current value stays.
>
> — `spec.md §6.1, Abuse cases, verbatim`

**Fallback:** insufficient or contradicted by the code → read [sad.md](../sad.md) §8 and [adr/0005](../adr/0005-store-theme-and-timezone-as-columns-on-the-owner-row.md). Do not guess.

## Data delta

No DB changes.

## API contract

Internal — no API surface (T5 serves the list).

## Acceptance criteria

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

- [ ] `Theme` enum (`LIGHT`, `DARK`, `SYSTEM`) with wire value lowercase and a `fromWire(String): Theme?` — `identity/Theme.kt`
- [ ] `TimeZones`: sorted list of `Area/City` ids (contain `/`, region prefix one of the IANA areas, ≤ 64 chars) plus `UTC`; `isKnown(id)`; computed once — `identity/TimeZones.kt`
- [ ] Unit tests: `Europe/Kyiv`, `America/Argentina/Buenos_Aires`, `UTC` known; `""`, `Etc/GMT+3`, `EST`, `Europe/Atlantis`, 65-char strings unknown; list sorted and unique — `src/test/kotlin/telex/identity/`

## Edge cases

| Case | Behaviour |
|---|---|
| Legacy aliases (`EST`, `GMT0`, `Etc/*`, `SystemV/*`) | Not on the list |
| Three-part ids (`America/Argentina/Buenos_Aires`) | On the list |
| Empty or blank id | `isKnown` = false |

## Definition of Done

- [ ] Unit tests for `Theme` and `TimeZones` pass (`./gradlew :backend:app:test`)
- [ ] No Spring bean required to use them (plain Kotlin objects at the `identity` root)
- [ ] `./gradlew spotlessCheck detekt` clean
