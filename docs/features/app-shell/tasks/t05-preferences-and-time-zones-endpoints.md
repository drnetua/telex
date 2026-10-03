---
id: T5
title: "Serve me with preferences, PATCH /me/preferences, the detected-timezone save and GET /time-zones"
layer: "ports"
deps: ["T3"]
blocks: ["T17", "T19"]
acs: ["AC-179", "AC-182", "AC-183", "AC-184", "AC-186"]
files_hint: ["backend/app/src/main/kotlin/telex/web/api/MeController.kt", "backend/app/src/main/kotlin/telex/web/api/TimeZonesController.kt", "backend/app/src/integrationTest/kotlin/telex/web/PreferencesApiIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T5 — Serve me with preferences, PATCH /me/preferences, the detected-timezone save and GET /time-zones

## Place in the sequence

- **Blocked by:** T3 — Read and change the Owner's theme and timezone in `identity` · **Blocks:** T17 — e2e: navigation, return after sign-in and the shell sweep, T19 — e2e: theme and timezone · **Wave:** 3.
- **Lane:** own lane (only task touching `MeController.kt`).

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

This task exposes the preference rules from T3 to the SPA through the derived contract.

## Inlined context

> **Authentication:** Every new endpoint (`pulse`, `me/preferences`, `time-zones`) needs a live Sign-in Session through the existing cookie filter.
> **Error handling:** RFC 9457 problems. New codes: `unknown-time-zone` (a timezone not on the list) and `time-zone-required` (an empty timezone, AC-186), both field errors on `timeZone`. A theme outside light, dark or system is a `validation-failed` field error.
>
> — `sad.md §8, Authentication + Error handling, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** Preferences are read and written only for the Owner of the current session. There is no way to name another Owner.
>
> — `spec.md §6.1, AuthZ + Abuse cases, abridged` · full text: [spec.md](../spec.md)

> Conventions: every POST/PATCH carries `X-XSRF-TOKEN` (missing or wrong → `403 forbidden`); a request with `X-Telex-Background: 1` does not count as session activity; every response carries `Cache-Control: no-store`.
>
> — `contracts/openapi.yaml, info.description, abridged`

**Fallback:** insufficient or contradicted by the code → read [openapi.yaml](../contracts/openapi.yaml) and [api-sync-report.md](../contracts/api-sync-report.md). Do not guess.

## Data delta

No DB changes (reads and writes go through `identity.OwnerPreferences`, T3).

## API contract

- `GET /api/v1/me` (`getMe`) → `200 Me` = `{ownerId, email, linkedAccountCount, theme, timeZone (TimeZoneId | null), timeZoneIsFallback}`, `additionalProperties: false` · `401`.
- `PATCH /api/v1/me/preferences` (`changeMyPreferences`), body `PreferencesChange {theme?, timeZone?}` — a property left out stays; empty body answers current preferences → `200 Preferences {theme, timeZone, timeZoneIsFallback}` · `400 validation-failed` with `errors[]` field codes `time-zone-required` (null/""), `unknown-time-zone`, `unknown-theme` (field `theme`, message "Choose light, dark or system.") · `401` · `403`. Picking a timezone clears `timeZoneIsFallback`.
- `POST /api/v1/me/preferences/detected-time-zone` (`saveDetectedTimeZone`), body `DetectedTimeZone {timeZone: string(≤64) | null}`, carries `X-Telex-Background` → `200 Preferences` (saved now, or saved earlier by another device). Never refuses a value: unknown/null saves `UTC` + `timeZoneIsFallback: true` · `401` · `403`.
- `GET /api/v1/time-zones` (`listTimeZones`) → `200 TimeZoneList {items: TimeZoneId[]}`, sorted, whole, no pagination · `401`.
- Field-error message for both timezone codes: "Choose a timezone from the list."

— `contracts/openapi.yaml, operationIds getMe, changeMyPreferences, saveDetectedTimeZone, listTimeZones, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-179 — happy

> **Given** a signed-in Owner on Profile and security with the light theme
> **When** they choose Dark
> **Then** every screen of teleX switches to the dark theme at once, without a reload, and the choice is saved to their account
>
> — `spec.md §5, AC-179, verbatim` · full text: [spec.md](../spec.md)

### AC-182 — error

> **Given** an Owner who changes the theme while teleX can't save it (for example, offline)
> **When** the save fails
> **Then** the theme returns to the previous choice and the Owner is told the change wasn't saved and can try again
>
> — `spec.md §5, AC-182, verbatim` · full text: [spec.md](../spec.md)

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

For AC-182 this task owns only the server side: a refused theme (`unknown-theme`) leaves the stored value unchanged; the revert and message are T9.

## Checklist

- [ ] `MeBody` gains `theme`, `timeZone`, `timeZoneIsFallback` — `web/api/MeController.kt`
- [ ] `PATCH /api/v1/me/preferences` and `POST /api/v1/me/preferences/detected-time-zone` in `MeController`, mapping `OwnerPreferences` problems to `validation-failed` + field `errors[]`
- [ ] `TimeZonesController`: `GET /api/v1/time-zones` from `identity.TimeZones` — `web/api/TimeZonesController.kt`
- [ ] `PreferencesApiIT` on Testcontainers, validating responses with the existing `ContractValidator` against `docs/features/app-shell/contracts/openapi.yaml` — one test per bullet in the API contract and per edge case below

## Edge cases

| Case | Behaviour |
|---|---|
| `PATCH {"timeZone": null}` or `{"timeZone": ""}` | `400`, field `timeZone` code `time-zone-required`; stored zone unchanged |
| `PATCH {"timeZone": "Europe/Atlantis"}` | `400`, `unknown-time-zone`; unchanged |
| `PATCH {"theme": "blue"}` | `400`, field `theme` code `unknown-theme`; unchanged |
| `PATCH {}` | `200` with current preferences |
| PATCH without `X-XSRF-TOKEN` | `403 forbidden` |
| `POST detected-time-zone` when a zone is already saved | `200` with the saved zone; nothing written |
| `POST detected-time-zone {"timeZone": null}` on an unset Owner | `200 {timeZone: "UTC", timeZoneIsFallback: true}` |
| Any of the four without a session | `401 unauthenticated` / `session-ended` |
| Background-marked detected-timezone save | Does not bump session activity |

## Definition of Done

- [ ] `PreferencesApiIT` covers every edge case and passes contract validation
- [ ] every Hard Rule inlined above still holds (session Owner only, CSRF on writes, `no-store`)
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green
