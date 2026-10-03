---
id: T14
title: "Save the device timezone on first open and show SCR-64 dates in the Owner's timezone"
layer: "ui"
deps: ["T9", "T10"]
blocks: ["T15"]
acs: ["AC-183"]
files_hint: ["frontend/src/shell/time.ts", "frontend/src/shell/time.test.ts", "frontend/src/api/preferences.ts", "frontend/src/app/layouts.tsx", "frontend/src/pages/profile-security/relativeTime.ts", "frontend/src/pages/profile-security/PasskeysCard.tsx", "frontend/src/pages/profile-security/SessionsCard.tsx", "frontend/src/pages/profile-security/ProfileSecurityPage.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "S"
status: "todo"
---

# T14 — Save the device timezone on first open and show SCR-64 dates in the Owner's timezone

## Place in the sequence

- **Blocked by:** T9 — `api/preferences.ts` and SCR-64 lane, T10 — `app/layouts.tsx` (where the first save is triggered) · **Blocks:** T15 — Time zone card and `TimeZonePicker` · **Wave:** 4.
- **Lane:** shares `api/preferences.ts` and `pages/profile-security/` with T9 and T15 — serialized by the dependency chain.

## Why (user story)

> **As an** Owner
> **I want** teleX to know my timezone and show dates and times in it
> **So that** times on every screen match my day, wherever my browser runs
>
> — `spec.md §4, US-74, verbatim` · full text: [spec.md](../spec.md)

This task gives every Owner a saved timezone without asking, and routes every date through it.

## Inlined context

> **Time and timezone:** The server sends instants in UTC (ISO 8601). Every date shown goes through `formatInstant(instant, timeZone)` (`Intl.DateTimeFormat` with the Owner's saved zone). The device zone comes from `Intl.DateTimeFormat().resolvedOptions().timeZone`, and is saved only while none is saved, through a server-side "only if unset" write. A zone off the list, or one that can't be read, saves UTC with `time_zone_is_fallback = true`.
>
> — `sad.md §8, Time and timezone, abridged` · full text: [sad.md](../sad.md)

> Flow 7: load the account → no timezone saved → read the device's timezone (a named region, or nothing readable) → save only if none is saved yet → dates shown in the saved timezone. opt the save gets no answer → Status Banner per seed flow 2, the next open tries the save again. opt later opens teleX on a device in another timezone → timezone already saved → no save.
>
> — `sad.md §6, Flow 7, abridged` · full text: [sad.md](../sad.md)

> SCR-64 `tz-not-yet`: `Me.timeZone = null` while `saveDetectedTimeZone` runs → `LoadState` (`rows=1`) in the Time zone card. **Dates:** every date or time shown goes through `formatInstant(instant, Me.timeZone)`. In E06 that means the Passkeys and Sessions dates on SCR-64. "Created … · Last used …" and "Active …" use `Me.timeZone`.
>
> — `screens.md §SCR-64 rows tz-not-yet, passkeys-*, sessions-* + §Shared conventions Dates, abridged` · full text: [screens.md](../screens.md)

**Fallback:** read [sad.md](../sad.md) Flow 7 and [adr/0005](../adr/0005-store-theme-and-timezone-as-columns-on-the-owner-row.md). Do not guess.

## Data delta

No DB changes.

## API contract

- `POST /api/v1/me/preferences/detected-time-zone` (`saveDetectedTimeZone`), `X-XSRF-TOKEN` + `X-Telex-Background: 1`, body `{timeZone: string | null}` → `200 Preferences {theme, timeZone, timeZoneIsFallback}` (saved now or earlier by another device; never refuses) · `401` · `403`. Sent only when `getMe` answers `timeZone: null`.

— `contracts/openapi.yaml, operationId saveDetectedTimeZone, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-183 — happy

> **Given** an Owner who has no timezone saved yet
> **When** they open teleX after this change
> **Then** their timezone is taken from the device and saved to their account (UTC, with a hint on Profile and security to pick their own, when the device's timezone can't be read or isn't on the list), Profile and security shows it, a device in another timezone later doesn't change it, and the dates on that page (when a Sign-in Session or Passkey was last used) are shown in it
>
> — `spec.md §5, AC-183, verbatim` · full text: [spec.md](../spec.md)

This task delivers the save and the dates; the card that shows the zone and the fallback hint is T15.

## Checklist

- [ ] `time.ts`: `deviceTimeZone(): string | null` (try/catch), `formatInstant(iso, timeZone, opts?)`, `useSaveDetectedTimeZone(me)` firing once per `me` with `timeZone === null` — `frontend/src/shell/time.ts`
- [ ] `saveDetectedTimeZone` call; on `200` write the returned preferences into the cached `me` — `frontend/src/api/preferences.ts`
- [ ] Trigger the hook from `AppLayout` once `me` is loaded — `frontend/src/app/layouts.tsx`
- [ ] `formatDate` / `formatWhen` take the Owner's zone; Passkeys and Sessions cards pass `me.timeZone` — `frontend/src/pages/profile-security/`
- [ ] Vitest: `formatInstant` across a date line (`Pacific/Auckland` vs `UTC`); hook sends the device zone once, sends `null` when unreadable, sends nothing when a zone is saved

## Edge cases

| Case | Behaviour |
|---|---|
| `Intl` throws or returns `undefined` | Send `timeZone: null` (server saves UTC + fallback) |
| `me.timeZone` already set | No request |
| Save gets no answer | Connectivity banner (T7); dates fall back to the device zone until the next open retries |
| Instant near midnight UTC | Shown on the Owner-zone date, not the device's |

## Definition of Done

- [ ] Vitest for every edge case passes; `ProfileSecurityPage.test.tsx` updated for zone-aware dates
- [ ] `pnpm run check` clean
