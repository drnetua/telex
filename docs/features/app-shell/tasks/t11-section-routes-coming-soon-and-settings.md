---
id: T11
title: "Route every section lazily, with the Coming soon page (SCR-94) and the Settings page (SCR-69)"
layer: "ui"
deps: ["T10"]
blocks: ["T17"]
acs: ["AC-171", "AC-172"]
files_hint: ["frontend/src/app/AppRoutes.tsx", "frontend/src/pages/coming-soon/", "frontend/src/pages/settings/", "frontend/src/shell/sections.ts", "frontend/src/App.test.tsx"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "S"
status: "todo"
---

# T11 — Route every section lazily, with the Coming soon page (SCR-94) and the Settings page (SCR-69)

## Place in the sequence

- **Blocked by:** T10 — Port AppShell from the section registry · **Blocks:** T17 — e2e: navigation, return after sign-in and the shell sweep · **Wave:** 4.
- **Lane:** shares `shell/sections.ts` with T10 (dependency already serializes them).

## Why (user story)

> **As an** Owner
> **I want** one navigation that shows every part of teleX on every signed-in screen
> **So that** I always know where I am and can get anywhere in one or two taps
>
> — `spec.md §4, US-70, verbatim` · full text: [spec.md](../spec.md)

This task gives each section its own address and a visible page, so the Owner sees the whole map from day one.

## Inlined context

> **Sections as routes** (`/overview`, `/inbox`, `/chats`, `/assistants`, `/runs`, `/tasks`, `/settings`, and the existing `/profile`), each lazy-loaded so the first signed-in screen stays within 2.5 s p75 on fast 4G. Unbuilt sections render SCR-94 at their own address.
>
> — `sad.md §4, UI architecture, verbatim` · full text: [sad.md](../sad.md)

> Unbuilt sections show a "Coming soon" page. Each "Coming soon" page is a deliberate, visible state that the owning epic must replace (E04, E09, E14, E22, E29), not a hidden flag.
>
> — `spec.md §1, Decision deviation, abridged` · decision: [adr/0001](../adr/0001-shell-scope-moves-offline-banner-coming-soon-panel.md)

> SCR-94 `default`: inside `AppShell`, that section current: h1 = section name, `Badge tone="neutral" icon="clock"` "Coming soon", `EmptyState kind="none"` with the section's icon, one sentence, action "Go to Inbox" → SCR-10. Sentences (draft, PM review pending before implement): Overview "See your day at a glance: what assistants did, what waits for you and what it cost." · Chats "Read and answer your Telegram chats, with assistants working alongside you." · Assistants "Set up the assistants that work for you, with their rules and limits." · Runs "Follow every time an assistant worked, step by step." · Tasks "Handle the tasks and approvals assistants hand to you."
> SCR-69 `default`: inside `AppShell`, Settings current: h1 "Settings", one Tabler `list-group` from a settings registry. E06 has one row: `Icon user` + h4 "Profile and security" + `small` "Passkeys, sign-in sessions, theme and time zone" + `chevron-right` → SCR-64. SCR-64 shows **Settings** current.
> Shell `section-loading`: chunk downloading → `LoadState state="loading"` in content, navigation stays. `section-load-failed`: chunk can't download → `EmptyState kind="blocked"` (icon `wifi-off`) "This section didn't load." + "Try again"; the banner explains why.
>
> — `screens.md §SCR-94, §SCR-69, §Shell rows section-*, abridged` · full text: [screens.md](../screens.md)

> **Hard rule:** Later epics add their Settings row and replace their section entry without changing the shell (QG-3a); sentences and labels live in `frontend/src/messages.ts`.
>
> — `sad.md §10 QG-3a + §8 Internationalisation, abridged`

**Fallback:** read [screens.md](../screens.md) W-94, W-69, W-S5. Open question on the Coming soon sentences: use the draft above until PM signs off. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — no API surface.

## Acceptance criteria

### AC-171 — happy

> **Given** a signed-in Owner and a section that its epic hasn't built yet (for example Runs)
> **When** they open that section
> **Then** they see a "Coming soon" page that names the section, says in one sentence what it will hold, and offers a way back to the Inbox, while the navigation stays in place with that section marked as current
>
> — `spec.md §5, AC-171, verbatim` · full text: [spec.md](../spec.md)

### AC-172 — happy

> **Given** a signed-in Owner on either width
> **When** they open Settings
> **Then** they see a Settings page that lists its subsections (in E06 only Profile and security; later epics add theirs to this list without changing the navigation), Profile and security is one step away, and Sign out is reachable from the shell on every signed-in screen; signing out ends the Sign-in Session and shows the sign-in page
>
> — `spec.md §5, AC-172, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Routes generated from `sections.ts` under `AppLayout`, each `React.lazy` + `Suspense` (`section-loading`) + chunk-error boundary (`section-load-failed`); `/` → `/inbox`; `/profile` marks Settings current — `frontend/src/app/AppRoutes.tsx`
- [ ] `ComingSoonPage` (one component, keyed by section) — `frontend/src/pages/coming-soon/`
- [ ] `SettingsPage` from a settings registry with the one Profile and security row — `frontend/src/pages/settings/`
- [ ] Copy into `frontend/src/messages.ts`
- [ ] Vitest: each of the five unbuilt sections renders its name, sentence and "Go to Inbox" with that section current; Settings lists Profile and security and links to `/profile`; chunk failure shows `section-load-failed` with Try again

## Edge cases

| Case | Behaviour |
|---|---|
| Direct link `/runs` | SCR-94 Runs inside the shell (session permitting) |
| Chunk download fails | `EmptyState blocked` "This section didn't load." + Try again; navigation stays |
| `/profile` open | Settings marked current |
| Unknown path | Existing E01 not-found page |

## Definition of Done

- [ ] Vitest for every edge case passes
- [ ] `pnpm run check` and `pnpm run build` clean; build output shows one chunk per section
