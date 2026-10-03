---
id: T8
title: "Apply the theme before first paint, follow System, sync tabs and switch once to the account theme"
layer: "ui"
deps: []
blocks: ["T9"]
acs: ["AC-180", "AC-181"]
files_hint: ["frontend/index.html", "frontend/src/shell/theme.ts", "frontend/src/shell/theme.test.ts", "frontend/src/api/account.ts", "frontend/src/api/account.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "S"
status: "todo"
---

# T8 — Apply the theme before first paint, follow System, sync tabs and switch once to the account theme

## Place in the sequence

- **Blocked by:** — · **Blocks:** T9 — Build `ThemeSwitch` and save the theme · **Wave:** 1.
- **Lane:** own lane (the `Me` type in `api/account.ts` is only extended here).

## Why (user story)

> **As an** Owner
> **I want** to choose a light, dark or system theme once and have it on all my devices
> **So that** teleX is comfortable to read at any time of day
>
> — `spec.md §4, US-73, verbatim` · full text: [spec.md](../spec.md)

This task makes the right theme appear from the first frame on every device and follow the device while on System.

## Inlined context

> **Theme:** `data-bs-theme` on `<html>` (Tabler's attribute, tokens from `styles.css` for both themes). An inline script applies the theme last used on this device (localStorage `telex.theme`, System resolved through `matchMedia`) before first paint. After `me` arrives the shell switches once if the account differs. System follows `prefers-color-scheme` changes live, and other tabs in the same browser follow through the `storage` event. A change applies before it saves and reverts if the save fails.
>
> — `sad.md §8, Theme, verbatim` · full text: [sad.md](../sad.md)

> Flow 6: read the theme last used on this device → *used before*: apply it before first paint, System resolved through the device's mode · *first time*: follow the device's mode until the account theme is known → load the account → *differs*: switch once, remember it on this device · *same*: no switch. opt System + device switches → every screen follows, no reload. opt another tab changes the theme → this tab follows.
>
> — `sad.md §6, Flow 6, abridged` · full text: [sad.md](../sad.md)

> `theme-switch`: `getMe.theme` differs from the theme applied at first paint (AC-181, Flow 6) → one switch of `data-bs-theme`, no reload, no transition animation. **Theme everywhere:** every page renders in the theme last used on this device from the first frame. Only signed-in screens switch to the account theme after `getMe` answers.
>
> — `screens.md §Shell table row theme-switch + §Shared conventions, abridged` · full text: [screens.md](../screens.md)

> NFR Theme switch: applied ≤ 200 ms after the choice, no reload; on a device used before, the first frame shows the theme last used there and at most one switch follows (only when the account's theme changed elsewhere).
>
> — `spec.md §6, Theme switch, abridged`

**Fallback:** insufficient or contradicted by the code → read [sad.md](../sad.md) §8 Theme and Flow 6. Do not guess.

## Data delta

No DB changes. (Theme last used on this device: browser `localStorage` `telex.theme` — `data-model.md §Not stored`.)

## API contract

- Reads `GET /api/v1/me` (`getMe`): `Me` gains `theme: "light"|"dark"|"system"`, `timeZone: string|null`, `timeZoneIsFallback: boolean`.

— `contracts/openapi.yaml, schema Me, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-180 — happy

> **Given** an Owner who chose System
> **When** their device switches between light and dark mode while teleX is open
> **Then** teleX follows the device without a reload
>
> — `spec.md §5, AC-180, verbatim` · full text: [spec.md](../spec.md)

### AC-181 — happy

> **Given** an Owner who chose Dark on their laptop
> **When** they sign in on their phone for the first time
> **Then** the phone shows the dark theme from the first signed-in screen; on a device where they've used teleX before, the first screen shows the theme last used there, and if the account's theme has changed meanwhile teleX switches once to it; devices already open pick up a theme changed elsewhere the next time teleX is opened or reloaded there
>
> — `spec.md §5, AC-181, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Inline `<script>` in `<head>` (before CSS paint): read `telex.theme` in try/catch, resolve System via `matchMedia('(prefers-color-scheme: dark)')`, set `data-bs-theme` — `frontend/index.html`
- [ ] `theme.ts`: `applyTheme(choice)`, `rememberTheme(choice)`, `currentChoice()`, System listener on `matchMedia` change, `storage` listener for other tabs, `useAccountTheme(me)` that switches once when `me.theme` differs — `frontend/src/shell/theme.ts`
- [ ] Extend `Me` with `theme`, `timeZone`, `timeZoneIsFallback` — `frontend/src/api/account.ts`
- [ ] Vitest: first-paint resolution for each stored value, System follow, storage sync, single switch after `me` — `frontend/src/shell/theme.test.ts`

## Edge cases

| Case | Behaviour |
|---|---|
| `localStorage` blocked or empty | Follow the device's mode until `me` answers |
| Stored `dark`, account `dark` | No switch after `me` |
| Stored `light`, account `dark` | Exactly one switch to dark, then remember `dark` |
| Choice System, device flips mode | `data-bs-theme` follows live, no reload |
| Another tab stores a new choice | This tab follows via `storage` |
| Signed-out pages (SCR-01, -92, -93) | Keep the device's remembered theme; no account switch |

## Definition of Done

- [ ] `theme.test.ts` covers every edge case and passes
- [ ] `pnpm run check` clean; `pnpm run build` keeps the inline script in `index.html`
