# Changelog — app-shell

## app-shell — one responsive shell around every signed-in screen, with a live Inbox counter, Status Banners, and theme and timezone saved on the account

**What:** every signed-in screen now sits inside one shell.

- **Desktop (768 px and wider):** a side menu lists all seven sections in the app-map order: Overview, Inbox, Chats, Assistants, Runs, Tasks, Settings.
- **Phone:** a bottom bar holds at most five items, and the rest sit under "More". The Inbox, with its counter, never moves under "More".
- **Start screen:** the Inbox opens first.
- **Inbox counter:** it updates live and is scoped to the signed-in Owner.
- **Unbuilt sections** (Overview, Chats, Assistants, Runs, Tasks) open a "Coming soon" page at their own address. The page names the section, says in one sentence what it will hold, and offers "Go to Inbox".
- **Settings** lists its subsections. In E06 that is only Profile and security. Sign out is reachable from the shell on every screen.
- **Status Banner:** a banner area under the header shows conditions that stop teleX from working. The first condition is "you're offline": the device has no network, or teleX doesn't answer, for example while it restarts. The banner has Try again, can't be dismissed while its condition holds, and keeps the Owner on their screen.
- **Theme:** the Owner picks light, dark or system on Profile and security, in the sidebar footer, or under More. The choice is saved on the account, applied before first paint on every device, and synced across tabs. System follows the device. If a save fails, the theme reverts and a toast offers to try again.
- **Timezone:** saved on the account. The device's timezone is stored on first open, with a "pick your own" hint when it had to fall back to UTC. The Owner can change it from a list searchable by city or region, and can't clear it once set. Dates on Profile and security show in the Owner's timezone.

**Why:** every later UI epic needs a frame to live in. Without a shared shell, each one would invent its own navigation, phone layout and status messages. An Owner must also be able to run teleX from a phone as fully as from a laptop (D-04) ([spec](../spec.md) §1, §2). Key decisions:

- [ADR-0001](../adr/0001-shell-scope-moves-offline-banner-coming-soon-panel.md): scope changes against the E06 card:
  - Unbuilt sections show a visible "Coming soon" page that the owning epic replaces.
  - The first banner is "you're offline". E02 adds "account disconnected".
  - The adaptive panel (C-05) moves to E14.
- [ADR-0002](../adr/0002-poll-one-background-pulse-every-3-seconds-for-live-signals.md): live signals (the Inbox count and banner conditions) come from one background pulse, `GET /api/v1/pulse`, every 3 s.
- [ADR-0003](../adr/0003-aggregate-the-inbox-count-from-sources-in-a-new-inbox-module.md): a new `inbox` Modulith module adds up the count from `InboxSource`s that later epics contribute. With no sources the count is 0.
- [ADR-0004](../adr/0004-detect-offline-from-pulse-failures-and-browser-network-state.md): offline is detected from pulse failures plus the browser's network state. Inside the shell, a request with no answer, a network error, or a 502/503/504 shows the banner instead of the full "teleX is unavailable" page. This narrows E01's AC-102.
- [ADR-0005](../adr/0005-store-theme-and-timezone-as-columns-on-the-owner-row.md): theme and timezone are columns on the `owner` row.
- [ADR-0006](../adr/0006-extend-the-shell-through-client-section-and-server-condition-registries.md): later epics extend the shell through a client section registry (`sections.ts`) and a server condition-source registry. They don't edit the shell.

**How to use:** sign in as before (`docker compose up`, http://localhost:8080). The shell is the signed-in app. Theme and timezone are on Settings → Profile and security. The API is in [openapi.yaml](../contracts/openapi.yaml):

- `GET /api/v1/pulse`: `{ inboxCount, conditions[] }` for the signed-in Owner. The SPA polls it every 3 s.
- `GET /api/v1/me` now also returns `theme`, `timeZone` and `timeZoneIsFallback`.
- `PATCH /api/v1/me/preferences`: change the theme and/or timezone. An empty timezone is rejected.
- `POST /api/v1/me/preferences/detected-time-zone`: the first-open save from the device. It never overwrites a zone that is already saved.
- `GET /api/v1/time-zones`: the known timezone list for the picker.
- `/api/v1/e2e-fixtures/pulse` exists only under the `e2e` Spring profile, which `compose.e2e.yaml` sets. Never enable that profile in production.

**Operational notes:**
- Migration: `V202610031152__add_owner_preferences` runs automatically on startup. It adds `theme` (default `system`), `time_zone` (nullable) and `time_zone_is_fallback` to `owner`. The constant defaults make it metadata-only on Postgres 17, so it needs no backfill. The rollback script `db/rollback/U202610031152__add_owner_preferences.sql` drops the three columns. `MigrationRollbackIT` proves up → down → up.
- Config:
  - HTTP compression is now on in `application.yaml`, so the SPA bundle loads within the phone budget.
  - No new environment variables.
  - The `e2e` profile is set only in `compose.e2e.yaml`.
- Rollback: revert the deploy, then apply the `U202610031152` script. Owners lose their saved theme and timezone. Before reverting, check that no later migration depends on these columns.
- Load: each open tab sends one pulse request every 3 s, and an Owner with several tabs sends one per tab. See `sad.md` §7 for the thresholds.

**Acceptance criteria delivered:** AC-170, AC-43, AC-07b, AC-171, AC-172, AC-173, AC-174, AC-175, AC-176, AC-177, AC-178, AC-179, AC-180, AC-181, AC-182, AC-183, AC-184, AC-185, AC-186. These are all 19 ACs in spec §5. E01's AC-102 is narrowed by AC-176.

**Spec deviations (recorded in spec §1):**
- "Coming soon" pages stand in for unbuilt sections, although the shared DoD forbids stubs.
- The first banner is "you're offline", not "account disconnected".
- The adaptive panel (C-05) and the full-screen half of AC-07b move to E14.
- The size is M instead of S.
- The timezone joins E06.
- AC-102 is narrowed: inside the shell, no answer or no network shows the banner instead of the full page.

**Known behaviour and open items (spec §8):**
- Accepted at ship (2026-10-03):
  - R2-4: after a failed latest theme save and a successful earlier one, the device stays on the reverted theme until the next load.
  - R2-13: a timezone save retried from SCR-93 saves the zone but doesn't show the "Time zone saved." toast.
- Dropped at ship: the visual-regression tier (B7). The reason is recorded in `test-plan.md`.
- Still open, with an owner and a due date:
  - E5: a hung call while the pulse still answers makes the banner flap.
  - The hand-offs to E02, E04, E09, E11, E14, E22 and E29.
  - A failed timezone save has no AC yet (screens.md noted gap 4).
