## Summary

E06 app-shell: one responsive shell around every signed-in screen. It has a side menu on desktop, a bottom bar plus "More" on phone, a live Inbox counter, "Coming soon" pages for unbuilt sections, the Status Banner mechanism with "you're offline / teleX isn't responding" as its first condition, and theme and timezone saved on the Owner's account. See the [spec](docs/features/app-shell/spec.md) and the [changelog](docs/features/app-shell/_ship/changelog.md).

## Acceptance criteria

- AC-170: desktop side menu with the seven sections in app-map order. The Inbox is the start screen, and the current section is marked by more than color ✓
- AC-43: phone bottom bar with at most five items, the rest under "More", and the Inbox with its counter always in the bar ✓
- AC-07b: every shell screen is usable at 360 px in both themes with no sideways scroll ✓
- AC-171: an unbuilt section opens "Coming soon" with its name, one sentence and "Go to Inbox", and the nav stays in place ✓
- AC-172: Settings lists its subsections (Profile and security), and Sign out is reachable from the shell everywhere ✓
- AC-173: without a session, nothing of the shell shows. A deep link leads to sign-in and back, and a dead session leads to "Session ended" ✓
- AC-174: the Inbox counter updates live across browsers of the same Owner ✓
- AC-175: the Inbox counter is scoped to the signed-in Owner ✓
- AC-176: with no network, or no answer from teleX, the Status Banner shows and the screen is kept (this narrows E01's AC-102) ✓
- AC-177: Try again while still down shows "Still can't reach teleX", and the banner clears by itself on recovery ✓
- AC-178: a banner can't be dismissed while its condition holds. When several hold, the most important one shows, with "+N more" ✓
- AC-179: choosing Dark applies at once and is saved on the account ✓
- AC-180: System follows the device's light or dark mode live ✓
- AC-181: a theme chosen on one device is applied before first paint on another ✓
- AC-182: a failed theme save reverts the theme and offers Try again ✓
- AC-183: the device's timezone is saved on first open, with UTC and a "pick your own" hint when it can't be read ✓
- AC-184: another timezone can be picked from a list searchable by city or region ✓
- AC-185: a search with no match says so ✓
- AC-186: a saved timezone can't be cleared ✓

## Design

- Spec: `docs/features/app-shell/spec.md`
- Architecture: `docs/features/app-shell/sad.md`
- Decisions: `docs/features/app-shell/adr/` (ADR-0001 to ADR-0006)
- Data model + migration: `docs/features/app-shell/data-model.md` (migration `V202610031152__add_owner_preferences`)
- API: `docs/features/app-shell/contracts/openapi.yaml`
- Screens / flows / test plan: `screens.md`, `ux-flows.md`, `test-plan.md`
- Reviews: `docs/features/app-shell/_review/`. There were six passes, and the last one is a PASS.

## Tasks (SDD-Task trailers)

- T2 — `d8f7a5c` feat(app-shell): add the Theme type and the known timezone list to identity
- T1 — `52b26a8` feat(app-shell): promote the staged owner-preferences migration into the live Flyway tree
- T4 — `0914e01` feat(app-shell): create the inbox module and the shared status condition source contract
- T8 — `5df48d2` feat(app-shell): apply the theme before first paint, follow System, sync tabs and switch once to the account theme
- T16 — `3e302f1` feat(app-shell): land a brand-new account on the remembered section after the passkey step
- T7 — `f1a7dae` feat(app-shell): add the connectivity state and the 3 s pulse query, and narrow fetch-client failure routing
- T3 — `ebec0cd` feat(app-shell): read and change the Owner's theme and timezone in identity, with the save-if-unset write
- T6 — `1f06fc7` feat(app-shell): serve the pulse and the e2e-profile fixture Inbox and condition sources
- T9 — `5fd3c5c` feat(app-shell): build ThemeSwitch, the Toast action prop and the Appearance card on SCR-64
- T10 — `c3b3edd` feat(app-shell): port AppShell from the section registry with side menu, phone bar, More sheet and Sign out
- T5 — `868f2a3` feat(app-shell): serve me with preferences, patch me/preferences, the detected-timezone save and the time-zone list
- T11 — `bc58c24` feat(app-shell): route every section lazily, with the Coming soon page and the Settings page
- T12 — `9e3af0b` feat(app-shell): show the live inbox counter in the shell from the pulse
- T14 — `c1a332f` feat(app-shell): save the device timezone on first open and show SCR-64 dates in the Owner's timezone
- T13 — `239094d` feat(app-shell): port StatusBanner with the condition catalog, Try again, still-down and N more
- T15 — `94c6de4` feat(app-shell): build the time zone card with the fallback hint and the searchable timezonepicker on SCR-64
- T17 — `2de90e7` test(app-shell): e2e navigation on both widths, return after sign-in, and the axe / width / target / load sweep
- T18 — `4eb3ee9` test(app-shell): e2e live Inbox counter and Status Banners with timing, and the narrowed AC-102
- T19 — `93e3eaf` test(app-shell): e2e theme choice, System follow, cross-device first paint, save failure and the timezone flows
- T20 — `2282c85` fix(app-shell): make the preferences and pulse endpoints honour the contract
- T21 — `dcc4467` fix(app-shell): keep one shared theme state: applied choice, save sequence, pending, revert and the failed-save toast, and route 401/5xx preference-save failures
- T23 — `d8f3940` fix(app-shell): remember the section on session ended, clear the cache on sign-in and keep a pulse 5xx on the banner
- T22 — `d8489e0` fix(app-shell): make the theme switches keyboard-correct
- T24 — `9870d56` fix(app-shell): keep More sheet focus and trap Tab, announce the first Status Banner, lift toasts above the phone bar
- T25 — `7e78f7d` fix(app-shell): build the time zone card's tz-not-yet state, return focus after a pick, and replace the tautological me test
- T26 — `2811762` test(app-shell): tighten the shell and preference e2e proofs
- T27 — `08b4b13` docs(app-shell): bring the docs in line with the shipped shell
- T28 — `7dc82ac` fix(app-shell): leave the shell even with blocked storage, and revert a failed theme save only over its own choice
- T29 — `0f71ae8` fix(app-shell): close the theme menu on Tab and when focus leaves it, with items out of the Tab order
- T30 — `86a5164` fix(app-shell): list unknown-property and pattern in the contract and cap fixture condition codes at 63 characters
- T31 — `3367643` test(app-shell): assert the narrowed AC-102 after the action's own 10 s timeout fires
- T32 — `5e6ad3a` docs(app-shell): bring the docs in line after the second review
- T33 — `18977d0` fix(app-shell): keep a superseded theme save's server failure off SCR-93, so its Retry can't bring the old theme back
- T34 — `07aed49` docs(app-shell): bring the docs in line after the third review
- T35 — `02443d1` fix(app-shell): re-check at retry time that another tab hasn't replaced a failed theme save
- T36 — `7aad8d3` docs(app-shell): say 502/503/504 reads as not responding only inside the shell, and drop the W-10b wireframe
- T37 — `2975021` docs(app-shell): say no answer and network errors read as not responding only inside the shell

## Verification

Run on 2026-10-03 at HEAD `092f50c`, plus the ship-time docs edits, on macOS with Docker Desktop.

- **Unit:** `./gradlew build integrationTest` passed (BUILD SUCCESSFUL, 7m 26s):
  - 62 backend unit tests, including `ModularityTest` (`ApplicationModules.verify()`).
  - The frontend `pnpm run check` (tsc, ESLint, Prettier and Vitest 275/275 in 29 files).
- **Integration:** 135 tests on Testcontainers pgvector. They include `MigrationRollbackIT` (up → down → up) and the contract checks against `openapi.yaml`.
- **Lint + vet:** detekt, ktlint (spotless), ESLint and `tsc --noEmit` are clean.
- **e2e (Playwright, phone 360 px + desktop 1280 px):** run against `compose.yaml + compose.e2e.yaml` built from HEAD.
  - The first full run was 99/104. The 5 failures were all desktop sign-up tests stuck on "Sending email" for more than 10 s.
  - The second full run was 103/104. The 1 failure was a different test: AC-178 timed out waiting 5 s for a fixture banner.
  - Every failed test passed on re-run, and `live-signals.spec.ts` passed 66/66 with `--repeat-each=3`.
  - The host was heavily loaded (load average about 39 on 12 cores, with another telex stack and other containers running), so these look like timing flakes under load, not regressions. CI on this PR is the clean-host signal. The fourth review pass had 104/104.
- **Ran the feature** against the live stack:
  - **AC-175:** Owner A set 5 waiting items. A's pulse returned `{"inboxCount":5}` and Owner B's returned `{"inboxCount":0}`.
  - **AC-183:** a fresh Owner started with `timeZone: null`.
    - Detecting `Europe/Kyiv` saved it, and a second detection of `Pacific/Auckland` didn't overwrite it.
    - Detecting `null` on another Owner saved `UTC` with `timeZoneIsFallback: true`.
    - Picking `America/Argentina/Buenos_Aires` then cleared the fallback flag.
  - **AC-186:** a PATCH with `timeZone: ""` and one with `null` both returned 400 `validation-failed` / `time-zone-required`, and the saved zone stayed.
  - **AC-179 / AC-181:** Dark was PATCHed from one session. A new sign-in, as if on a phone, read `theme: dark`. In a real Chromium at 360 px, the shell rendered with `data-bs-theme="dark"` from first load.
  - **AC-43 / AC-07b / AC-171:** at 360 px the bottom bar was Inbox, Chats, Assistants, Tasks, More, and `scrollWidth` was 360. More → Runs opened "Runs · Coming soon · Follow every time an assistant worked, step by step. · Go to Inbox", with More marked current.
  - **AC-176 / AC-177 / AC-178:** I ran `docker stop` on the app while on Runs.
    - The banner "teleX isn't responding." appeared with Try again and no close button, and the screen stayed on Runs.
    - Try again while still down showed "Still can't reach teleX. It keeps trying on its own."
    - After `docker start`, the banner cleared by itself. The Owner was still on Runs and still signed in.
  - **AC-173:** `GET /api/v1/pulse` without a session returned 401 `unauthenticated`.
- **Not verified here:**
  - Real iOS or Android devices. Phone width was checked only in Chromium at 360 px.
  - The live doc behind `docs/docs/03-product-spec.md` needs the same SCR-69, SCR-94 and SCR-95 rows that were added to the snapshot here.
- **Nit seen while running:** `/favicon.ico` returns 404, which shows as a console error. It is not in this feature's scope.

## Ship-time doc changes

- Added SCR-69, SCR-94 and SCR-95 to `03-product-spec.md`, closing screens.md noted gap 5.
- Settled three spec §8 questions that were due before ship:
  - B7: the visual-regression tier is dropped, with the reason in `test-plan.md`.
  - R2-4 and R2-13 are accepted as known behaviour.

## Operational notes

- Migration: `V202610031152__add_owner_preferences` runs on startup and adds `theme`, `time_zone` and `time_zone_is_fallback` to `owner`. It is metadata-only on Postgres 17. Roll back with `db/rollback/U202610031152__add_owner_preferences.sql`, after reverting the deploy.
- Config: HTTP compression is now on. There are no new environment variables. The `e2e` Spring profile, with its fixture endpoint, is set only by `compose.e2e.yaml`.
- Load: each open tab polls `GET /api/v1/pulse` every 3 s (ADR-0002, `sad.md` §7).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
