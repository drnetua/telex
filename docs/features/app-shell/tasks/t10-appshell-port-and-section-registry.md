---
id: T10
title: "Port AppShell (C-01) from the section registry: side menu, phone bottom bar, More sheet and Sign out"
layer: "ui"
deps: ["T9"]
blocks: ["T11", "T12", "T13", "T14"]
acs: ["AC-170", "AC-43", "AC-07b", "AC-172"]
files_hint: ["frontend/src/shell/AppShell/", "frontend/src/shell/sections.ts", "frontend/src/app/layouts.tsx", "frontend/src/app/layouts.test.tsx", "frontend/src/components/PageFrame/", "frontend/src/components/Icon/Icon.tsx"]
owner: "Anton Husiev"
estimate: "L"
context_budget: "M"
status: "todo"
---

# T10 — Port AppShell (C-01) from the section registry: side menu, phone bottom bar, More sheet and Sign out

## Place in the sequence

- **Blocked by:** T9 — Build `ThemeSwitch` (footer and More use it) · **Blocks:** T11 — Section routes, Coming soon and Settings, T12 — Live Inbox counter, T13 — StatusBanner, T14 — Dates in the Owner's timezone · **Wave:** 3.
- **Lane:** shares `shell/AppShell/` with T12 and T13 and `shell/sections.ts` with T11 — all depend on this task, so they follow it.

## Why (user story)

> **As an** Owner
> **I want** one navigation that shows every part of teleX on every signed-in screen
> **So that** I always know where I am and can get anywhere in one or two taps
>
> — `spec.md §4, US-70, verbatim` · full text: [spec.md](../spec.md)

> **As an** Owner
> **I want** to use teleX from my phone as fully as from my laptop
> **So that** I'm not tied to a computer
>
> — `spec.md §4, US-43, verbatim`

This task builds the frame every signed-in screen lives in, on both widths.

## Inlined context

> **AppShell (C-01)** as the layout of every signed-in route, replacing `PageFrame`. Above 768 px it shows a side menu, and below 768 px a bottom bar of five items plus a "More" sheet. Both are generated from the section registry (ADR-0006). "More" is a sheet inside the shell, not an address.
> `sections.ts` — section registry: id, path, label, icon, phone placement (bar | more), page | Coming soon.
>
> — `sad.md §4 UI architecture + §5 Internal decomposition, abridged` · decision: [adr/0006](../adr/0006-extend-the-shell-through-client-section-and-server-condition-registries.md)

> AppShell port: sections in the reference `NAV` order and icons: Overview `layout-dashboard`, Inbox `inbox`, Chats `messages`, Assistants `sparkles`, Runs `activity`, Tasks `checklist`, Settings `settings`. Links are real routes. Phone bar: Inbox, Chats, Assistants, Tasks and **More**; Overview, Runs, Settings under More. Header in E06: **no Stop all and no account switcher**; on phone it shows the current section's name; on desktop no header bar, banner slot at the top of the main column. Desktop sidebar footer: Owner's email, `ThemeSwitch` (menu), Sign out; on phone these are under More.
>
> — `screens.md §Shell, AppShell port, abridged` · full text: [screens.md](../screens.md)

> Shell states: `starting` — `getMe` in flight; nothing of the shell (`LoadState rows=3`, device theme) · `default-desktop` — ≥ 768 px, side menu 240 px, brand "tX teleX", seven sections, footer email + `ThemeSwitch variant="menu"` + ghost `logout` "Sign out" · `default-phone` — < 768 px, header with section name, bottom nav 5 items ≥ 44 × 44 px (icon above label), Inbox item always present · `current-section` — `aria-current="page"`, 3 px `primary` bar at inline start (desktop) / top (phone), `primary-subtle` bg, **bold** label; a section under More marks **More** current · `signing-out` — busy "Signing out" → cache cleared, pulse stops → SCR-01.
> SCR-95 More (phone only): `offcanvas offcanvas-bottom` (`aria-label` "More", focus moves in), title + close ghost `x` "Close"; `list-group` Overview, Runs, Settings (≥ 44 px, current marked); divider; "Theme" row `ThemeSwitch variant="segmented"`; `logout` "Sign out". `closed`: close, tap outside, Escape or system back → focus returns to "More". `navigated` → sheet closes. Theme change keeps the sheet open. Crossing 768 px closes it.
>
> — `screens.md §Shell table + §SCR-95, abridged` · full text: [screens.md](../screens.md)

> **Hard rule:** UI epics may later touch only `sections.ts` / `conditions.ts` entries under `frontend/src/shell/` (QG-3a). Breakpoint 768 px (`bp-tablet`); every shell screen fits 360 px with no sideways scroll; tokens only; status never by color alone.
>
> — `sad.md §10 QG-3a + §8 Responsive layout, abridged`

**Fallback:** port from `docs/docs/design-system/components/AppShell/` (README, `index.d.ts`, `bundle.js`) with the deltas above; read [screens.md](../screens.md) W-S1, W-S2, W-95 in full. Do not guess.

## Data delta

No DB changes.

## API contract

- Reads `GET /api/v1/me` (`getMe`) for `email`; Sign out reuses the existing E01 `useSignOut()`.

— `contracts/openapi.yaml, operationId getMe, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-170 — happy

> **Given** a signed-in Owner on a screen at least 768 px wide
> **When** they open teleX
> **Then** the Inbox opens as the start screen, a side menu lists the seven sections in the app-map order (Overview, Inbox, Chats, Assistants, Runs, Tasks, Settings), and the current section is marked by more than color alone
>
> — `spec.md §5, AC-170, verbatim` · full text: [spec.md](../spec.md)

### AC-43 — happy

> **Given** a signed-in Owner on a screen narrower than 768 px
> **When** they move between sections
> **Then** the navigation sits at the bottom of the screen with at most five items, the sections that don't fit are one tap away under "More", and the Inbox item with its counter is always in the bottom bar and never moves under "More"
>
> — `spec.md §5, AC-43, verbatim` · full text: [spec.md](../spec.md)

### AC-07b — happy

> **Given** a signed-in Owner on a 360 px wide phone screen in either theme
> **When** they open any screen of the shell (any section, "More", Profile and security, a "Coming soon" page, a Status Banner)
> **Then** every action on it is reachable without scrolling sideways
>
> — `spec.md §5, AC-07b, verbatim` · full text: [spec.md](../spec.md)

### AC-172 — happy

> **Given** a signed-in Owner on either width
> **When** they open Settings
> **Then** they see a Settings page that lists its subsections (in E06 only Profile and security; later epics add theirs to this list without changing the navigation), Profile and security is one step away, and Sign out is reachable from the shell on every signed-in screen; signing out ends the Sign-in Session and shows the sign-in page
>
> — `spec.md §5, AC-172, verbatim` · full text: [spec.md](../spec.md)

This task owns the "Sign out reachable from the shell on every signed-in screen" half of AC-172; the Settings page is T11.

## Checklist

- [ ] `sections.ts`: the seven entries (id, path, `messages` label key, icon, `phone: "bar" | "more"`, lazy page or `"coming-soon"`) — `frontend/src/shell/sections.ts`
- [ ] `AppShell` port: desktop side menu, phone header + bottom bar, `MoreSheet` (SCR-95), footer, current-section marking, a `banner` slot and an Inbox `count` prop for T12/T13 — `frontend/src/shell/AppShell/`
- [ ] Add icons `inbox`, `messages`, `sparkles`, `activity`, `checklist`, `settings`, `layout-dashboard`, `dots`, `x` to the subset — `frontend/src/components/Icon/Icon.tsx`
- [ ] `AppLayout` renders `AppShell` (with the `starting` state while `getMe` loads); delete `components/PageFrame/` — `frontend/src/app/layouts.tsx`
- [ ] Copy into `frontend/src/messages.ts`
- [ ] Vitest: order of seven items; phone bar = Inbox, Chats, Assistants, Tasks, More; More lists Overview, Runs, Settings; `aria-current`; More current for a More section; Sign out ends the session and lands on SCR-01; Escape closes More and returns focus

## Edge cases

| Case | Behaviour |
|---|---|
| `getMe` still loading | `starting`: no navigation, counter or banner |
| Section under More is current (phone) | More is marked current |
| More open and width crosses 768 px | Sheet closes |
| Sign out | Busy "Signing out" → cache cleared, pulse stopped → SCR-01 |
| 360 px, long email | Footer/More wraps; no sideways scroll |

## Definition of Done

- [ ] Vitest for every edge case passes; `layouts.test.tsx` updated for the removed `PageFrame`
- [ ] `pnpm run check` clean; no raw hex, no emoji
- [ ] `AppShell` inventory row in `docs/design-system.md` flipped to `frontend/src/shell/AppShell/`; `PageFrame` row removed
