---
status: Draft
owner: "Anton Husiev"
reviewers: ["Tech Lead", "Security Lead"]
updated_at: "2026-10-03"
feature_size: "M"
---

# Spec — app-shell

> **Glossary:** [CONTEXT](../../../CONTEXT.md) (repo root; no feature-level CONTEXT.md)
> **Reference module / docs / channels used:** `docs/docs/02-epics.md` §E06 + shared DoD · `docs/docs/03-product-spec.md` §Карта застосунку, SCR-10, SCR-64, SCR-90, C-01…C-05, D-04, D-14, D-15, D-17, D-18, D-20 · `docs/docs/img/app-map.png` · `docs/docs/design-system/README.md` · `docs/features/platform-skeleton/spec.md` §3, `screens.md` (`PageFrame`) · `docs/roadmap.md` step 6 · `frontend/src/app/`, `frontend/src/components/PageFrame/`, `e2e/playwright.config.ts`.

## 1. Context

teleX is a web Telegram client that an Owner should be able to run from a phone as fully as from a laptop (D-04). Today a signed-in Owner sees a temporary page frame from E01: a wordmark, a Profile button and Sign out. There's no way to move between parts of teleX, no place to learn that something stops teleX from working, no dark theme and no timezone. Every later UI epic (Chats, Assistants, Runs, Tasks, Settings pages) needs a frame to live in. Without a shared shell, each one would invent its own navigation, phone layout and status messages.

Why now: E01 has shipped, so a signed-in Owner exists, and wave 3 of the roadmap starts E02 (Telegram link) and E10 (model profiles) in parallel. Those are the first epics that add real sections. If the shell lands with them, every page from E04 on is built inside it rather than retrofitted. E01 also deferred the theme switcher and timezone on Profile and security to this epic.

Committed approach: one shell around every signed-in screen. On a desktop-width screen it's a side menu with all seven sections from the app map (Overview, Inbox, Chats, Assistants, Runs, Tasks, Settings). On a phone-width screen it's a bottom bar of at most five items with the rest under "More". The Inbox, with its counter, is always one tap away, and the Inbox stays the start screen until the Overview arrives (D-20). Sections that later epics haven't built yet open a "Coming soon" page instead of being hidden, so the Owner sees the whole map from day one. Each epic replaces its own page. A Status Banner mechanism sits under the header. Its first real condition is "you're offline" (the browser lost its connection to teleX), which needs no other epic and matters most on phones. The Owner's theme (light, dark or system) and timezone are stored on their account, so they follow them to every device. The design-system tokens work in both themes, and every UI scenario runs at phone and desktop widths. Success means an Owner on a 360 px phone reaches every section and sees the Inbox counter without horizontal scrolling, and no later UI epic needs to change the shell beyond replacing its "Coming soon" page or adding its Status Banner.

Traceability:
- Epic E06 features 1–4. Feature 1 (shell with Inbox counter) is US-70/US-71. Feature 2 (Status Banner mechanism) is US-72. Feature 3 (design system: tokens, base components, light and dark themes) is US-73 plus the §6 rows. Feature 4 (phone and desktop test profiles in CI) already exists from E01; this spec makes "every UI scenario on both profiles" a §6 rule and adds the accessibility scan in both themes. US-43, AC-07b and AC-43 keep their epic ids. New ids use the block US-70+ and AC-170+, because parallel specs (telegram-link, model-profiles) are allocating ids right after the E01 range.
- Decision deviation: unbuilt sections show a "Coming soon" page, while the shared DoD forbids stubs. The reason is that the Owner should see the full map of teleX from day one. Each "Coming soon" page is a deliberate, visible state that the owning epic must replace (the E04, E09, E14, E22, E29 specs each inherit that task), not a hidden flag ([ADR-0001](./adr/0001-shell-scope-moves-offline-banner-coming-soon-panel.md)).
- Decision deviation: the first real Status Banner is "you're offline", while E06 names "account disconnected". The reason is that Linked Accounts only arrive with E02, which runs in parallel. Waiting for it would add a 2 → 6 dependency and tie E06 to the Telegram-library risk (roadmap D1). E02 adds "account disconnected" on top of this mechanism ([ADR-0001](./adr/0001-shell-scope-moves-offline-banner-coming-soon-panel.md)).
- Decision deviation: the adaptive panel (C-05, a side panel on desktop that opens full screen on phone) and the "side panels open full-screen" half of AC-07b move to the first epic that has a panel (E14, SCR-41 run details). No E06 screen has a panel, and a component without a screen can't be checked end to end ([ADR-0001](./adr/0001-shell-scope-moves-offline-banner-coming-soon-panel.md); the E14 card in `02-epics.md` now lists it).
- Decision deviation: the size is M, while `02-epics.md` sizes E06 as S. The reason is that the theme and timezone saved on the account add a migration, and they come with new account-preference reads and writes plus a live Inbox counter (reclassified after the critic pass).
- Decision deviation: the timezone joins E06. SCR-64 lists it, and the E01 spec deferred it here. Later features (the morning digest in E20, quiet hours in E19) read it.
- Decision deviation: E01's AC-102 showed the full-page "teleX is unavailable" when an action got no answer within 10 seconds. E06 narrows it: no answer, or no network, now shows the Status Banner and keeps the screen (AC-176), and the full page stays only for an action that teleX answers with a failure. The reason is that on a phone a short drop or a teleX restart shouldn't throw the Owner off their screen.
- Prerequisite: the design canon `docs/design-system.md` (`/sdd:design-system`, E06 DoD) is set up before `ux-flows`/`screens` run for this feature.

## 2. Goals

- An Owner reaches every part of teleX from any signed-in screen, on a phone as easily as on a laptop, and always sees how much waits for them in the Inbox.
- An Owner always knows when teleX can't serve them right now, and what they can do about it, without losing their place.
- teleX looks the same everywhere in the Owner's chosen theme and timezone, and every later screen inherits the shell, theme and banners instead of rebuilding them.

## 3. Non-goals

- The Stop all button (C-03) and the Resume flow. They arrive with the action itself in E23. A button with nothing to stop would mislead.
- The account switcher (C-02). It needs more than one Linked Account and a chat context, which arrive in E02 and E04.
- The adaptive panel (C-05) and the responsive layout of specific later screens. Each belongs to the epic that builds the screen (§1 deviation).
- Navigation into the Operator console. Operator-only pages and the "no access" page arrive with E26.
- Working offline. When the connection drops, the Owner is told and can retry, but teleX doesn't cache content for offline reading. That would be a separate product decision, and nothing on the roadmap asks for it.
- A collapsible sidebar or a customisable section order. Seven fixed sections fit a desktop sidebar, and fewer choices keep the shell predictable for later epics.

## 4. User stories

The Operator has no story here. E06 changes nothing the Operator does (installation, quotas, model catalog), and the Operator console's navigation is E26.

### US-43: Use teleX fully from a phone

**As an** Owner
**I want** to use teleX from my phone as fully as from my laptop
**So that** I'm not tied to a computer

### US-70: Reach any section from anywhere

**As an** Owner
**I want** one navigation that shows every part of teleX on every signed-in screen
**So that** I always know where I am and can get anywhere in one or two taps

### US-71: See what waits for me

**As an** Owner
**I want** the Inbox counter in the navigation to always show how many items wait for me
**So that** I notice new work without opening the Inbox

### US-72: Know when teleX can't serve me

**As an** Owner
**I want** a Status Banner whenever something stops teleX from working for me, with one thing I can do about it
**So that** I don't mistake a broken connection for an empty Inbox or a lost action

### US-73: Choose my theme

**As an** Owner
**I want** to choose a light, dark or system theme once and have it on all my devices
**So that** teleX is comfortable to read at any time of day

### US-74: See times in my timezone

**As an** Owner
**I want** teleX to know my timezone and show dates and times in it
**So that** times on every screen match my day, wherever my browser runs

## 5. Acceptance criteria

### AC-170 (US-70) — happy

**Given** a signed-in Owner on a screen at least 768 px wide
**When** they open teleX
**Then** the Inbox opens as the start screen, a side menu lists the seven sections in the app-map order (Overview, Inbox, Chats, Assistants, Runs, Tasks, Settings), and the current section is marked by more than color alone

### AC-43 (US-43) — happy

**Given** a signed-in Owner on a screen narrower than 768 px
**When** they move between sections
**Then** the navigation sits at the bottom of the screen with at most five items, the sections that don't fit are one tap away under "More", and the Inbox item with its counter is always in the bottom bar and never moves under "More"

### AC-07b (US-43) — happy

**Given** a signed-in Owner on a 360 px wide phone screen in either theme
**When** they open any screen of the shell (any section, "More", Profile and security, a "Coming soon" page, a Status Banner)
**Then** every action on it is reachable without scrolling sideways

### AC-171 (US-70) — happy

**Given** a signed-in Owner and a section that its epic hasn't built yet (for example Runs)
**When** they open that section
**Then** they see a "Coming soon" page that names the section, says in one sentence what it will hold, and offers a way back to the Inbox, while the navigation stays in place with that section marked as current

### AC-172 (US-70) — happy

**Given** a signed-in Owner on either width
**When** they open Settings
**Then** they see a Settings page that lists its subsections (in E06 only Profile and security; later epics add theirs to this list without changing the navigation), Profile and security is one step away, and Sign out is reachable from the shell on every signed-in screen; signing out ends the Sign-in Session and shows the sign-in page

### AC-173 (US-70) — authorization

**Given** a visitor without a live Sign-in Session
**When** they open a link to any section, including a "Coming soon" one, or take their next action in a tab that was open when the session stopped
**Then** they see nothing of the shell (no sections, no counter, no banners): a person who never signed in or signed out sees the sign-in page, and a session that ran out or was revoked shows the "Session ended" page; when sign-in finishes in that same browser (Sign-in Code, Passkey, or a Sign-in Link opened there) they land on the section the link pointed to, otherwise on the Inbox, and a brand-new account first gets the passkey offer

### AC-174 (US-71) — cross-context

**Given** a signed-in Owner whose Inbox holds 3 waiting items, with teleX open on any section
**When** a new item lands in their Inbox, or one is resolved in another browser of the same Owner
**Then** the Inbox counter on the current screen changes to 4 (or 2) without a page reload; when no items wait, the Inbox item shows no number but stays in place, and above 99 it shows "99+". An item counts while the epic that put it in the Inbox says it waits for the Owner; E06 adds no kind of item itself, so until E11 the Inbox stays empty with its "Connect Telegram" step

### AC-175 (US-71) — authorization

**Given** two Owners, one with 5 waiting Inbox items and one with none
**When** the Owner with none opens teleX
**Then** their Inbox counter shows no number, and nothing in their shell reveals how many items any other Owner has

### AC-176 (US-72) — happy

**Given** a signed-in Owner on any screen of the shell
**When** their device has no network, or it has one but teleX doesn't answer (for example while teleX restarts)
**Then** a Status Banner under the header says so ("You're offline" or "teleX isn't responding"), with an icon and words (not color alone) and a "Try again" action; when the connection comes back, the banner disappears by itself and the current screen shows fresh data; the full-page "teleX is unavailable" from E01 now appears only when teleX answers an action with a failure

### AC-177 (US-72) — error

**Given** an Owner who sees the offline Status Banner
**When** they press "Try again" while the connection is still down
**Then** the banner stays, tells them teleX is still unreachable and that it keeps retrying on its own, and nothing they were viewing is cleared from the screen

### AC-178 (US-72) — domain invariant

**Given** an Owner on a screen where one or more Status Banner conditions hold
**When** they look for a way to close a banner, or a second condition starts while the first still holds
**Then** no banner can be dismissed while its cause remains ("a banner lives exactly as long as its cause"), and when several conditions hold only the most important banner shows, chosen by a fixed importance order (§8), with "N more" that lists the others, each with its own action

### AC-179 (US-73) — happy

**Given** a signed-in Owner on Profile and security with the light theme
**When** they choose Dark
**Then** every screen of teleX switches to the dark theme at once, without a reload, and the choice is saved to their account

### AC-180 (US-73) — happy

**Given** an Owner who chose System
**When** their device switches between light and dark mode while teleX is open
**Then** teleX follows the device without a reload

### AC-181 (US-73) — happy

**Given** an Owner who chose Dark on their laptop
**When** they sign in on their phone for the first time
**Then** the phone shows the dark theme from the first signed-in screen; on a device where they've used teleX before, the first screen shows the theme last used there, and if the account's theme has changed meanwhile teleX switches once to it; devices already open pick up a theme changed elsewhere the next time teleX is opened or reloaded there

### AC-182 (US-73) — error

**Given** an Owner who changes the theme while teleX can't save it (for example, offline)
**When** the save fails
**Then** the theme returns to the previous choice and the Owner is told the change wasn't saved and can try again

### AC-183 (US-74) — happy

**Given** an Owner who has no timezone saved yet
**When** they open teleX after this change
**Then** their timezone is taken from the device and saved to their account (UTC, with a hint on Profile and security to pick their own, when the device's timezone can't be read or isn't on the list), Profile and security shows it, a device in another timezone later doesn't change it, and the dates on that page (when a Sign-in Session or Passkey was last used) are shown in it

### AC-184 (US-74) — happy

**Given** an Owner on Profile and security
**When** they pick another timezone from the list (searchable by city or region)
**Then** the new timezone is saved, and every date and time teleX shows them uses it from then on, on all their devices (devices already open pick it up the next time teleX is opened or reloaded there)

### AC-185 (US-74) — error

**Given** an Owner searching the timezone list
**When** they type a place that matches no timezone
**Then** the list says nothing matches and suggests searching by a nearby city, and their current timezone stays unchanged

### AC-186 (US-74) — domain invariant

**Given** an Owner with a saved timezone
**When** they try to leave the timezone empty
**Then** teleX doesn't allow it and keeps the current one ("an Owner always has exactly one timezone"); the only way to change it is to pick another from the list

## 6. Non-functional requirements

| Aspect | Target | Measurement |
|---|---|---|
| Offline Status Banner appears | ≤ 5 s after the device loses its network or teleX stops answering | e2e with the network cut, both profiles |
| Offline Status Banner clears | ≤ 5 s after the connection returns | same e2e, connection restored |
| Inbox counter freshness | ≤ 5 s from an item landing or being resolved to the new number on screen | e2e with prepared Inbox items (no feature puts items in the Inbox until E11) |
| Theme switch | applied ≤ 200 ms after the choice, no reload; on a device used before, the first frame shows the theme last used there and at most one switch follows (only when the account's theme changed elsewhere) | e2e with a trace; the theme attribute is checked at the first paint |
| No sideways scroll | 0 shell screens wider than the viewport at 360 px and 1280 px, both themes | e2e check of page width on every shell screen |
| Accessibility | 0 serious or critical axe findings on every shell screen, both widths, both themes; bottom-bar targets ≥ 44 × 44 px; WCAG 2.2 AA contrast per D-18 | axe scan in e2e; target size checked in e2e |
| Both widths in CI | 100 % of UI e2e scenarios run in both the phone (360 px) and desktop (1280 px) profiles, from E06 on | CI report lists every scenario under both profiles |
| Browsers | latest two versions of Chrome and Safari, including Safari on iOS | manual pass before `/sdd:ship`, recorded in the PR |
| Shell load | first signed-in screen usable ≤ 2.5 s p75 on the phone profile with a simulated fast-4G network | Playwright performance trace in CI, phone profile, throttled |

## 6.1 Security / privacy

- **Data classification:** internal. Theme and timezone are the Owner's preferences, and the shell shows no chat content.
- **Personal data touched:** two new fields on the Owner's account. Theme (light, dark or system) has no sensitivity. Timezone (a named region, e.g. Europe/Kyiv) is low sensitivity because it hints at where the Owner lives. Neither is shown to anyone but the Owner. The Operator sees neither.
- **AuthZ/AuthN impact:** no new capability. Every section, "Coming soon" page, banner and the Inbox counter needs a live Sign-in Session (the E01 check). Preferences are read and written only for the Owner of the current session, and the counter counts only that Owner's Inbox items.
- **Abuse cases:**
  - Reading or changing another Owner's theme or timezone: there is no way to name another Owner, so only the current Owner's preferences are ever touched.
  - Inferring another Owner's activity from the Inbox counter: the counter counts only the current Owner's items (AC-175).
  - A tampered timezone value: only timezones from the known list are accepted. Anything else is refused and the current value stays.
  - Shell content leaking past session end: after sign-out or session end, the next screen is the sign-in or session-ended page. No section, counter or banner shows (AC-173).
- **Security review:** N/A. No new authorization boundary, and the new personal data (theme, timezone) is low sensitivity and owner-only under existing session checks.

## 7. Metrics / KPIs

- **UI scenarios run at both widths** — baseline: 100 % of the E01 suite, target: 100 % of every UI epic's e2e scenarios through gate G3, read from the CI report at each `/sdd:ship`.
- **Shell changes needed by later epics** — baseline: 0 (no later UI epic has shipped), target: 0 UI epics through G3 need to change the shell beyond adding their section, replacing their "Coming soon" page or adding their Status Banner, checked at each UI epic's `/sdd:review`.
- **Accessibility findings on shell screens** — baseline: unmeasured (no shell yet), target: 0 serious or critical findings in both themes and both widths at E06 ship and at every later release.
- **Phone-width defects on shell screens** — baseline: 0 open, target: 0 open "doesn't work on phone" defects against shell screens at gate G1.

## 8. Open questions

- [ ] Which four sections sit in the phone bottom bar next to "More"? Default now: Inbox, Chats, Assistants, Tasks in the bar; Overview, Runs, Settings under "More". — owner: Anton Husiev, due: before `/sdd:screens app-shell`
- [ ] When does the telegram-link spec gain its "Telegram account disconnected" Status Banner AC, built on this mechanism? Default now: in its clarify pass. — owner: Anton Husiev, due: before `/sdd:tasks telegram-link` (the E02 card now lists it as feature 7; the telegram-link spec still needs the AC)
- [ ] What's the importance order of all Status Banner conditions from C-04? Default now: offline > account disconnected > bot blocked > consent needed > budget exhausted > all assistants paused > triage deferred. — owner: Anton Husiev, due: before `/sdd:screens app-shell`
- [ ] Does the E14 (agent-runtime) spec pick up the adaptive panel and the "side panels open full screen on a phone" half of AC-07b? Default now: yes, with SCR-41 as the first screen. — owner: Anton Husiev, due: before `/sdd:specify agent-runtime`
- [ ] Do the specs of E04 (Chats), E09 (Assistants), E14 (Runs), E22 (Tasks) and E29 (Overview) each replace their "Coming soon" page? Default now: yes, as an AC in each, checked at its `/sdd:review`. — owner: Anton Husiev, due: at each epic's `/sdd:specify`
- [ ] Does the E11 (keyword-triggers) spec make its Inbox Notes count toward the Inbox counter, and say when a Note stops waiting? Default now: yes, a Note waits until the Owner opens or dismisses it. — owner: Anton Husiev, due: before `/sdd:specify keyword-triggers`
- [ ] Do we keep the visual-regression tier in `test-plan.md` (`toHaveScreenshot` baselines for the 3 px current-section bar and the "+N more" banner layout), or drop it with a recorded reason? Default now: deferred; `aria-current` + bold weight and the "+N more" text stay asserted by component and e2e tests. — owner: Anton Husiev, due: before `/sdd:ship app-shell` (from review 2026-10-03, B7)
- [ ] Should one hung call while the pulse keeps answering show "teleX isn't responding"? Today the banner shows at the 10 s fetch timeout and the next pulse clears it, so it flaps. Default now: keep the current behaviour. — owner: Anton Husiev, due: before `/sdd:specify` of the next feature that adds long-running actions (from review 2026-10-03, E5)
