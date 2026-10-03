---
id: T9
title: "Build ThemeSwitch (segmented and menu), the Toast action prop and the Appearance card on SCR-64"
layer: "ui"
deps: ["T8"]
blocks: ["T10", "T14"]
acs: ["AC-179", "AC-182"]
files_hint: ["frontend/src/components/ThemeSwitch/", "frontend/src/components/Toast/Toast.tsx", "frontend/src/api/preferences.ts", "frontend/src/pages/profile-security/AppearanceCard.tsx", "frontend/src/pages/profile-security/ProfileSecurityPage.tsx", "frontend/src/pages/profile-security/ProfileSecurityPage.test.tsx"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "S"
status: "todo"
---

# T9 — Build ThemeSwitch (segmented and menu), the Toast action prop and the Appearance card on SCR-64

## Place in the sequence

- **Blocked by:** T8 — Apply the theme before first paint · **Blocks:** T10 — Port AppShell (uses `ThemeSwitch variant="menu"` / `segmented`), T14 — Show dates in the Owner's timezone · **Wave:** 2.
- **Lane:** shares `api/preferences.ts` and `ProfileSecurityPage.tsx` with T14 and T15 — serialized (T14 depends on this task).

## Why (user story)

> **As an** Owner
> **I want** to choose a light, dark or system theme once and have it on all my devices
> **So that** teleX is comfortable to read at any time of day
>
> — `spec.md §4, US-73, verbatim` · full text: [spec.md](../spec.md)

This task is the one control that changes the theme, with apply-at-once and revert-on-failure.

## Inlined context

> `ThemeSwitch`: one theme control used in three places (SCR-64 Appearance, the desktop sidebar footer, More) with one behavior: apply at once, remember on this device, save, and revert with an error Toast + "Try again" on failure (AC-179, AC-182). Two variants: `segmented` (Tabler `form-selectgroup`, icon + word) and `menu` (a `Button` ghost showing the current theme's icon and "Theme", opening a `dropdown-menu` of `menuitemradio`).
> `Toast` — `action` prop: adds `action?: { label: string; onClick: () => void }`, rendered as `Button` link before the close button. The toast stays until dismissed or acted on.
>
> — `screens.md §New components, ThemeSwitch + Toast, abridged` · full text: [screens.md](../screens.md)

> SCR-64 `theme-default`: Card "Appearance": `ThemeSwitch variant="segmented"` (`aria-label` "Theme"): `sun` "Light", `moon` "Dark", `device-desktop` "System", saved one checked; `small` "System follows your device's light or dark mode." · `theme-applied`: `data-bs-theme` changes at once (≤ 200 ms), remembered on this device, `changeMyPreferences {theme}` sent; radios stay enabled, a newer choice supersedes the one in flight · `theme-saved`: no message · `theme-save-failed` (`400 unknown-theme`, `403`, or no answer): reverts everywhere incl. device memory; error `Toast` "Your theme wasn't saved." with action "Try again" (re-applies and re-sends). With no answer, the shell banner also shows. · `theme-system-follows`: page follows live; radios stay on System. · Shell `theme-menu-open`: `dropdown-menu` (opens upward), three `menuitemradio` items with icon + word, saved one checked, Escape closes and returns focus.
>
> — `screens.md §SCR-64 rows theme-* + §Shell theme-menu-open, abridged` · full text: [screens.md](../screens.md)

> Seed flow 3: apply dark at once and remember it on this device → change preferences → *saved*: shows Dark as the saved choice · *no answer or refused*: apply the previous theme and remember it on this device → says the change wasn't saved, offers Try again.
>
> — `sad.md §6, Critical flow 3, abridged`

> UI rules: tokens only (no raw hex), status never by color alone, sentence-case English copy in `frontend/src/messages.ts`, no emoji. Reuse `Button`, `Icon`, `Toast` from `frontend/src/components/`.
>
> — `CLAUDE.md §Quality gates + sad.md §2 Conventions, abridged`

**Fallback:** insufficient or contradicted by the code → read [screens.md](../screens.md) SCR-64 and §New components. Do not guess.

## Data delta

No DB changes.

## API contract

- `PATCH /api/v1/me/preferences` (`changeMyPreferences`) body `{theme}` → `200 Preferences` · `400 validation-failed` field `theme` code `unknown-theme` · `401` · `403`. On `200`, update the cached `me`.

— `contracts/openapi.yaml, operationId changeMyPreferences, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

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

## Checklist

- [ ] `useChangePreferences()` mutation + `useChangeTheme()` (optimistic apply/remember, revert on error, latest choice wins) — `frontend/src/api/preferences.ts`
- [ ] `Toast` gains `action?: { label; onClick }`; an error toast with an action stays until dismissed or acted on — `frontend/src/components/Toast/Toast.tsx`
- [ ] `ThemeSwitch` with `variant: "segmented" | "menu"`, icons `sun`/`moon`/`device-desktop` (add to `Icon` subset if missing), keyboard and ARIA per the rows above — `frontend/src/components/ThemeSwitch/`
- [ ] `AppearanceCard` above Passkeys on SCR-64 — `frontend/src/pages/profile-security/`
- [ ] Copy into `frontend/src/messages.ts`
- [ ] Vitest: apply ≤ one tick before the request resolves; revert + toast on 400 and on no answer; "Try again" re-sends; menu variant keyboard

## Edge cases

| Case | Behaviour |
|---|---|
| Two choices in quick succession | Latest wins; an earlier response doesn't revert the newer choice |
| Save fails | Theme and `telex.theme` revert; error toast with "Try again" |
| "Try again" pressed | Re-applies and re-sends the failed choice |
| Save gets no answer | Same revert + toast; connectivity banner also shows (T7) |

## Definition of Done

- [ ] Vitest for every edge case passes
- [ ] `pnpm run check` clean
- [ ] `ThemeSwitch` and `Toast` `action` registered in `docs/design-system.md` inventory (status from `pending` to built)
