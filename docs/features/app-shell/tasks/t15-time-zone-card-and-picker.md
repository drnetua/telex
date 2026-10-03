---
id: T15
title: "Build the Time zone card with the fallback hint and the searchable TimeZonePicker on SCR-64"
layer: "ui"
deps: ["T14"]
blocks: ["T19"]
acs: ["AC-183", "AC-184", "AC-185", "AC-186"]
files_hint: ["frontend/src/components/TimeZonePicker/", "frontend/src/pages/profile-security/TimeZoneCard.tsx", "frontend/src/pages/profile-security/ProfileSecurityPage.tsx", "frontend/src/pages/profile-security/ProfileSecurityPage.test.tsx", "frontend/src/api/preferences.ts"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T15 — Build the Time zone card with the fallback hint and the searchable TimeZonePicker on SCR-64

## Place in the sequence

- **Blocked by:** T14 — Save the device timezone and zone-aware dates · **Blocks:** T19 — e2e: theme and timezone · **Wave:** 5.
- **Lane:** shares `api/preferences.ts` and `ProfileSecurityPage.tsx` with T9 and T14 — serialized by the dependency chain.

## Why (user story)

> **As an** Owner
> **I want** teleX to know my timezone and show dates and times in it
> **So that** times on every screen match my day, wherever my browser runs
>
> — `spec.md §4, US-74, verbatim` · full text: [spec.md](../spec.md)

This task lets the Owner see and change their one timezone, with no way to leave it empty.

## Inlined context

> Flow 8: open the picker → get the known timezone list → type a city or region → filter on the device → *nothing matches*: says so, suggests a nearby city, current timezone unchanged · *tries to leave it empty*: no empty choice, current kept · *picks*: change → *on the list*: saved, SCR-64 shows it, every date re-renders · *empty*: refused, timezone required · *not on the list*: refused, unknown timezone · *no answer*: says it wasn't saved, current kept, Status Banner.
>
> — `sad.md §6, Flow 8, abridged` · full text: [sad.md](../sad.md)

> SCR-64 Time zone card (above Passkeys, below Appearance):
> `tz-default`: h4 "Kyiv" + `small` "Europe/Kyiv · UTC+03:00" (offset via `Intl`) + `Button` secondary "Change time zone"; `small` "Dates and times in teleX use this time zone on all your devices." · `tz-fallback-hint` (`timeZoneIsFallback`): shows "UTC" plus `alert` info-subtle `info-circle` "We couldn't read your device's time zone, so teleX uses UTC." + `Button` link "Choose yours" (opens picker) · `tz-not-yet` (`Me.timeZone = null` while the detected zone is being saved): `LoadState rows=1` in the card, no zone text and no Change button · `tz-picking`: `TimeZonePicker` in Tabler `modal` (`modal-dialog-centered`, max 480 px; full screen below 768 px, `modal-fullscreen-md-down`), header "Choose your time zone" + close ghost `x`; `form-control` with `Icon search`, label "Search by city or region", autofocus; `listbox` (`list-group`, scrolls) "Kyiv — Europe/Kyiv", current marked `check`; footer ghost "Cancel"; focus trapped, Escape closes, focus returns; no clear or empty option · `tz-list-loading`: `LoadState rows=3` · `tz-no-match`: `small` + `Icon search` "No time zone matches “{query}”. Try a nearby city." · `tz-list-failed`: "The time zone list didn't load." + link "Try again" · `tz-saving`: dialog closes, "Change time zone" busy ("Saving") · `tz-saved`: new zone shown, hint gone, dates re-render, `Toast` info "Time zone saved." · `tz-refused` (`400 unknown-time-zone` / `time-zone-required`): zone stays; `invalid-feedback` + `alert-circle` "Choose a time zone from the list." · `tz-save-failed` (no answer): zone stays; error `Toast` "Your time zone wasn't saved." + "Try again" (re-sends).
>
> — `screens.md §SCR-64 rows tz-*, abridged` · full text: [screens.md](../screens.md)

> `TimeZonePicker`: searchable single-choice list over about 400 items, keyboard ↑ ↓ Enter Escape, explicit no-match state, no empty choice; Tabler `modal` + `form-control` + `list-group` with the ARIA combobox/listbox pattern.
>
> — `screens.md §New components, TimeZonePicker, abridged`

**Fallback:** read [screens.md](../screens.md) W-64a–c. A failed timezone save has no AC (screens.md Noted gap 4); follow `tz-save-failed` as drawn. Do not guess.

## Data delta

No DB changes.

## API contract

- `GET /api/v1/time-zones` (`listTimeZones`) → `200 {items: TimeZoneId[]}` sorted, whole; fetched only when the picker opens.
- `PATCH /api/v1/me/preferences` (`changeMyPreferences`) body `{timeZone}` → `200 Preferences` (clears `timeZoneIsFallback`) · `400 validation-failed` field `timeZone` codes `time-zone-required` / `unknown-time-zone`.

— `contracts/openapi.yaml, operationIds listTimeZones, changeMyPreferences, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

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

### AC-185 — error

> **Given** an Owner searching the timezone list
> **When** they type a place that matches no timezone
> **Then** the list says nothing matches and suggests searching by a nearby city, and their current timezone stays unchanged
>
> — `spec.md §5, AC-185, verbatim` · full text: [spec.md](../spec.md)

### AC-186 — domain invariant

> **Given** an Owner with a saved timezone
> **When** they try to leave the timezone empty
> **Then** teleX doesn't allow it and keeps the current one ("an Owner always has exactly one timezone"); the only way to change it is to pick another from the list
>
> — `spec.md §5, AC-186, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `TimeZonePicker` (modal, search by any `/`-separated part with `_` as space, listbox keyboard, no-match, loading, failed) — `frontend/src/components/TimeZonePicker/`
- [ ] `TimeZoneCard` with every `tz-*` state above; placed between Appearance and Passkeys — `frontend/src/pages/profile-security/`
- [ ] `useListTimeZones()` (enabled on open) and `useChangeTimeZone()` mapping field codes to `tz-refused` — `frontend/src/api/preferences.ts`
- [ ] Copy into `frontend/src/messages.ts`; icons `search`, `check` if missing
- [ ] Vitest: hint shown only with `timeZoneIsFallback`; "kyiv" finds Europe/Kyiv; "Atlantis" → no-match, zone unchanged; no empty option; pick saves + toast + dates re-render; 400 → inline refusal; no answer → error toast with Try again

## Edge cases

| Case | Behaviour |
|---|---|
| Search "buenos aires" | Matches `America/Argentina/Buenos_Aires` |
| Search with no match | No-match text; Cancel keeps the current zone |
| Search cleared to empty | Full list again; nothing saved |
| Escape / Cancel / close | Dialog closes, focus returns to opener, nothing saved |
| Server `400 unknown-time-zone` | Zone kept, inline "Choose a time zone from the list." |
| Phone width | Picker is full screen; no sideways scroll at 360 px |

## Definition of Done

- [ ] Vitest for every edge case passes
- [ ] `pnpm run check` clean
- [ ] `TimeZonePicker` registered in `docs/design-system.md` inventory
