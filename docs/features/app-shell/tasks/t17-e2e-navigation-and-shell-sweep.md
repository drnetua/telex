---
id: T17
title: "e2e: navigation on both widths, return after sign-in, and the axe / width / target / load sweep"
layer: "tests"
deps: ["T5", "T6", "T11", "T13", "T16"]
blocks: ["T18", "T19"]
acs: ["AC-170", "AC-43", "AC-07b", "AC-171", "AC-172", "AC-173"]
files_hint: ["e2e/tests/shell-navigation.spec.ts", "e2e/tests/shell-sweep.spec.ts", "e2e/tests/touch-targets.spec.ts", "e2e/support/shell.ts", "e2e/playwright.config.ts", "compose.e2e.yaml", ".github/workflows/ci.yml"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T17 — e2e: navigation on both widths, return after sign-in, and the axe / width / target / load sweep

## Place in the sequence

- **Blocked by:** T5 — preference endpoints (sweep in both themes), T6 — pulse + `e2e` fixtures, T11 — section routes and pages, T13 — StatusBanner (banner screen in the sweep), T16 — return after sign-in · **Blocks:** T18 — e2e: Inbox counter and Status Banners, T19 — e2e: theme and timezone · **Wave:** 5.
- **Lane:** shares `e2e/support/shell.ts` with T18 and T19 — they follow this task.

## Why (user story)

> **As an** Owner
> **I want** to use teleX from my phone as fully as from my laptop
> **So that** I'm not tied to a computer
>
> — `spec.md §4, US-43, verbatim` · full text: [spec.md](../spec.md)

This task proves the shell end to end on both Playwright profiles and sets up the `e2e` backend profile that T18 and T19 rely on.

## Inlined context

> | No sideways scroll | 0 shell screens wider than the viewport at 360 px and 1280 px, both themes |
> | Accessibility | 0 serious or critical axe findings on every shell screen, both widths, both themes; bottom-bar targets ≥ 44 × 44 px; WCAG 2.2 AA contrast per D-18 |
> | Both widths in CI | 100 % of UI e2e scenarios run in both the phone (360 px) and desktop (1280 px) profiles, from E06 on |
> | Shell load | first signed-in screen usable ≤ 2.5 s p75 on the phone profile with a simulated fast-4G network |
>
> — `spec.md §6, NFR rows, abridged` · full text: [spec.md](../spec.md)

> QG-2a: e2e checks `document.documentElement.scrollWidth <= innerWidth` on every shell screen (each section, More, Settings, Profile and security, Coming soon, with a banner showing). QG-2b: axe per screen × width × theme, plus a bounding-box check of every bottom-bar item on the phone profile (extends `e2e/tests/touch-targets.spec.ts`). QG-2c: Playwright performance trace on the phone profile with fast-4G throttling, until the Inbox and its counter are interactive. QG-3b: a scenario marked for one project fails the check.
>
> — `sad.md §10, QG-2a…2c, QG-3b, abridged` · full text: [sad.md](../sad.md)

> The `e2e` Spring profile, which enables the fixture sources, is set only by the e2e run, never in `compose.yaml` or production. Add an e2e for "open a Runs link signed out → sign up → passkey offer → Runs".
>
> — `sad.md §7 Configuration + §11, abridged`

Existing harness: Playwright runs against `docker compose up` (`e2e/playwright.config.ts`, projects `phone` 360 × 800 and `desktop` 1280 × 800); `e2e/support/flows.ts` already has `expectNoA11yViolations(page, state)` (axe, WCAG 2.2 AA) and sign-up helpers; CI job `e2e` runs `docker compose up -d --build --wait`.

**Fallback:** read [sad.md](../sad.md) §10 and [screens.md](../screens.md) for each screen's states. Do not guess.

## Data delta

No DB changes.

## API contract

- Uses `PUT /api/v1/e2e-fixtures/pulse` (`setPulseFixture`) to show a banner for the sweep; `PATCH /api/v1/me/preferences` (`changeMyPreferences`) to flip the theme between sweep passes.

— `contracts/openapi.yaml, operationIds setPulseFixture, changeMyPreferences, abridged`

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
> — `spec.md §5, AC-43, verbatim`

### AC-07b — happy

> **Given** a signed-in Owner on a 360 px wide phone screen in either theme
> **When** they open any screen of the shell (any section, "More", Profile and security, a "Coming soon" page, a Status Banner)
> **Then** every action on it is reachable without scrolling sideways
>
> — `spec.md §5, AC-07b, verbatim`

### AC-171 — happy

> **Given** a signed-in Owner and a section that its epic hasn't built yet (for example Runs)
> **When** they open that section
> **Then** they see a "Coming soon" page that names the section, says in one sentence what it will hold, and offers a way back to the Inbox, while the navigation stays in place with that section marked as current
>
> — `spec.md §5, AC-171, verbatim`

### AC-172 — happy

> **Given** a signed-in Owner on either width
> **When** they open Settings
> **Then** they see a Settings page that lists its subsections (in E06 only Profile and security; later epics add theirs to this list without changing the navigation), Profile and security is one step away, and Sign out is reachable from the shell on every signed-in screen; signing out ends the Sign-in Session and shows the sign-in page
>
> — `spec.md §5, AC-172, verbatim`

### AC-173 — authorization

> **Given** a visitor without a live Sign-in Session
> **When** they open a link to any section, including a "Coming soon" one, or take their next action in a tab that was open when the session stopped
> **Then** they see nothing of the shell (no sections, no counter, no banners): a person who never signed in or signed out sees the sign-in page, and a session that ran out or was revoked shows the "Session ended" page; when sign-in finishes in that same browser (Sign-in Code, Passkey, or a Sign-in Link opened there) they land on the section the link pointed to, otherwise on the Inbox, and a brand-new account first gets the passkey offer
>
> — `spec.md §5, AC-173, verbatim`

## Checklist

- [ ] `compose.e2e.yaml` override setting `SPRING_PROFILES_ACTIVE=e2e` on the app; CI `e2e` job uses `docker compose -f compose.yaml -f compose.e2e.yaml up`; `compose.yaml` untouched — `compose.e2e.yaml`, `.github/workflows/ci.yml`
- [ ] Helpers: `setPulseFixture(page, {inboxCount, conditions})`, `setTheme(page, theme)`, `shellScreens` list, `expectNoSidewaysScroll(page)` — `e2e/support/shell.ts`
- [ ] Navigation spec: AC-170 (desktop), AC-43 (phone, More), AC-171 (each unbuilt section), AC-172 (Settings → Profile and security, Sign out from shell), AC-173 (signed-out link to `/runs` → SCR-01 → sign up → passkey offer → `/runs`; revoked session → SCR-92) — `e2e/tests/shell-navigation.spec.ts`
- [ ] Sweep spec: every shell screen × both themes: `expectNoA11yViolations` + no sideways scroll; one pass with a fixture banner showing — `e2e/tests/shell-sweep.spec.ts`
- [ ] Bottom-bar items ≥ 44 × 44 on phone — `e2e/tests/touch-targets.spec.ts`
- [ ] Fast-4G CDP throttling trace on phone: Inbox + counter interactive ≤ 2.5 s — in the sweep spec
- [ ] No `test.skip` by project: every scenario runs under `phone` and `desktop`

## Edge cases

| Case | Behaviour |
|---|---|
| Section link opened signed out | SCR-01, no nav/counter/banner in the DOM |
| Session revoked in another context, then an action | SCR-92 |
| Phone, a More section open | More marked current; Inbox still in the bar |
| Dark theme sweep | Same zero-violation and width checks |

## Definition of Done

- [ ] All new specs green under both Playwright projects in CI, against the `e2e` profile
- [ ] `compose.yaml` and `application.yaml` do not activate `e2e`
- [ ] `pnpm --filter @telex/e2e run check` clean
