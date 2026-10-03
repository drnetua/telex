---
id: T19
title: "e2e: theme choice, System follow, cross-device first paint, save failure, and the timezone flows"
layer: "tests"
deps: ["T5", "T15", "T17"]
blocks: []
acs: ["AC-179", "AC-180", "AC-181", "AC-182", "AC-183", "AC-184", "AC-185", "AC-186"]
files_hint: ["e2e/tests/preferences.spec.ts", "e2e/support/shell.ts"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T19 — e2e: theme choice, System follow, cross-device first paint, save failure, and the timezone flows

## Place in the sequence

- **Blocked by:** T5 — preference endpoints, T15 — Time zone card and picker (and through it the theme chain T8 → T9), T17 — e2e harness · **Blocks:** — · **Wave:** 6.
- **Lane:** shares `e2e/support/shell.ts` with T17 and T18 — serialized.

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
> — `spec.md §4, US-74, verbatim`

This task proves both preferences end to end, across two browser contexts standing in for two devices.

## Inlined context

> | Theme switch | applied ≤ 200 ms after the choice, no reload; on a device used before, the first frame shows the theme last used there and at most one switch follows (only when the account's theme changed elsewhere) | e2e with a trace; the theme attribute is checked at the first paint |
>
> — `spec.md §6, NFR Theme switch, verbatim`

> QG-2d: an e2e trace from the click to the change of the `data-bs-theme` attribute. The attribute is read at first paint with a remembered theme, and switch events are counted after `me` returns.
>
> — `sad.md §10, QG-2d, verbatim` · full text: [sad.md](../sad.md)

> Theme in localStorage `telex.theme`; System via `prefers-color-scheme` (Playwright `page.emulateMedia({ colorScheme })`). Device timezone via the browser context `timezoneId`. Theme save failure text: "Your theme wasn't saved." + "Try again". Timezone: "No time zone matches “{query}”. Try a nearby city."; fallback hint "We couldn't read your device's time zone, so teleX uses UTC."
>
> — `sad.md §8 Theme + screens.md §SCR-64, abridged` · full text: [screens.md](../screens.md)

**Fallback:** read [sad.md](../sad.md) Flows 6–8 and [screens.md](../screens.md) SCR-64. Do not guess.

## Data delta

No DB changes.

## API contract

- `PATCH /api/v1/me/preferences` (`changeMyPreferences`) — routed to abort for AC-182; sent raw with `{"timeZone": ""}` for AC-186 → `400 time-zone-required`.
- `POST /api/v1/me/preferences/detected-time-zone` (`saveDetectedTimeZone`) — observed on first open.

— `contracts/openapi.yaml, operationIds changeMyPreferences, saveDetectedTimeZone, abridged`

## Acceptance criteria

### AC-179 — happy

> **Given** a signed-in Owner on Profile and security with the light theme
> **When** they choose Dark
> **Then** every screen of teleX switches to the dark theme at once, without a reload, and the choice is saved to their account
>
> — `spec.md §5, AC-179, verbatim` · full text: [spec.md](../spec.md)

### AC-180 — happy

> **Given** an Owner who chose System
> **When** their device switches between light and dark mode while teleX is open
> **Then** teleX follows the device without a reload
>
> — `spec.md §5, AC-180, verbatim`

### AC-181 — happy

> **Given** an Owner who chose Dark on their laptop
> **When** they sign in on their phone for the first time
> **Then** the phone shows the dark theme from the first signed-in screen; on a device where they've used teleX before, the first screen shows the theme last used there, and if the account's theme has changed meanwhile teleX switches once to it; devices already open pick up a theme changed elsewhere the next time teleX is opened or reloaded there
>
> — `spec.md §5, AC-181, verbatim`

### AC-182 — error

> **Given** an Owner who changes the theme while teleX can't save it (for example, offline)
> **When** the save fails
> **Then** the theme returns to the previous choice and the Owner is told the change wasn't saved and can try again
>
> — `spec.md §5, AC-182, verbatim`

### AC-183 — happy

> **Given** an Owner who has no timezone saved yet
> **When** they open teleX after this change
> **Then** their timezone is taken from the device and saved to their account (UTC, with a hint on Profile and security to pick their own, when the device's timezone can't be read or isn't on the list), Profile and security shows it, a device in another timezone later doesn't change it, and the dates on that page (when a Sign-in Session or Passkey was last used) are shown in it
>
> — `spec.md §5, AC-183, verbatim`

### AC-184 — happy

> **Given** an Owner on Profile and security
> **When** they pick another timezone from the list (searchable by city or region)
> **Then** the new timezone is saved, and every date and time teleX shows them uses it from then on, on all their devices (devices already open pick it up the next time teleX is opened or reloaded there)
>
> — `spec.md §5, AC-184, verbatim`

### AC-185 — error

> **Given** an Owner searching the timezone list
> **When** they type a place that matches no timezone
> **Then** the list says nothing matches and suggests searching by a nearby city, and their current timezone stays unchanged
>
> — `spec.md §5, AC-185, verbatim`

### AC-186 — domain invariant

> **Given** an Owner with a saved timezone
> **When** they try to leave the timezone empty
> **Then** teleX doesn't allow it and keeps the current one ("an Owner always has exactly one timezone"); the only way to change it is to pick another from the list
>
> — `spec.md §5, AC-186, verbatim`

## Checklist

- [ ] Theme: Dark on SCR-64 → `data-bs-theme="dark"` ≤ 200 ms, no navigation event, `getMe.theme` = dark; System + `emulateMedia` flip → follows — `e2e/tests/preferences.spec.ts`
- [ ] Cross-device: context A sets Dark; fresh context B signs in → dark on first signed-in frame; context with remembered light + account dark → light at first paint, exactly one switch
- [ ] Save failure: route PATCH to abort → theme reverts, toast with Try again; Try again after unrouting → saved
- [ ] Timezone: context `timezoneId: "Asia/Tokyo"` first open → saved Asia/Tokyo; second context `America/New_York` → unchanged; dates on SCR-64 in Asia/Tokyo; pick "Kyiv" → saved and shown after reload in the other context; "Atlantis" → no-match, unchanged; picker has no empty option and raw `PATCH {"timeZone": ""}` → `400 time-zone-required`, value kept

## Edge cases

| Case | Behaviour |
|---|---|
| Unreadable device zone (stubbed `Intl`) | UTC saved; fallback hint on SCR-64 on every device |
| Theme changed on device A while B is open | B unchanged until reload, then switches once |

## Definition of Done

- [ ] Spec green under both Playwright projects in CI
- [ ] `pnpm --filter @telex/e2e run check` clean
