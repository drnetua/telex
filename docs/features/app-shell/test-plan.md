---
status: Draft
owner: "Anton Husiev (QA)"
reviewers: ["Anton Husiev (implementing engineer)", "Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
---

# Test plan — app-shell

One shell around every signed-in screen. On desktop widths it's a side menu with the seven app-map sections. On phone widths it's a bottom bar of five items plus "More". A live Inbox counter is driven by a 3 s pulse. Sections that aren't built yet open a "Coming soon" page. A Status Banner shows "You're offline" or "teleX isn't responding" and can't be closed while its cause holds. The theme (light, dark or system) and timezone are saved on the Owner's account. Coverage below maps every `spec.md` §5 AC (19) to named tests. The e2e-through-UI paths follow `ux-flows.md` and the `sad.md` §6 flows. The component states follow `screens.md`. Levels per AC were confirmed by the owner on 2026-10-03.

## Levels

`sad.md` declares `target_surfaces: [backend-service, web-frontend]`, so the UI tiers apply.

| Level | Scope | Strategy (generic — no tool names) |
|---|---|---|
| Unit | Pure rules with no I/O. Frontend: the section registry (order, phone placement), counter formatting, the connectivity state machine and failure classification, Status Banner selection (importance order, "N more", unknown codes), theme resolution (remembered theme at first paint, System from the device scheme, at most one switch), device timezone detection with the UTC fallback, the return-after-sign-in rule for a new account. Backend: `Theme` values, the known timezone list (`Area/City` + `UTC`), and the "never empty once set" rule. | In-memory. Browser inputs (network state, color-scheme query, device timezone, remembered values) are passed in or replaced with in-test doubles. |
| Integration | `identity` against the real Postgres it owns (theme and timezone columns, the save-only-if-unset write, the fallback flag and its check constraint). `inbox` + `web` pulse with the `e2e`-profile fixture sources. The security chain on the new endpoints (no session, ended session, background marker). Module boundaries (`inbox` depends on `shared` only, `shared` stays bean-free). Migration up → down → up. | A throwaway Postgres container (the repo's pgvector image) per suite, shared through the existing container configuration. Fixture Inbox and condition sources are in-memory beans that exist only in the `e2e` profile. No mocked datastore. |
| Contract | Real responses of `GET /me`, `PATCH /me/preferences`, `POST /me/preferences/detected-time-zone`, `GET /time-zones` and `GET /pulse`, including problem bodies, checked against `contracts/openapi.yaml`. | The real bodies produced in the integration tests are validated against the agreed document through the repo's existing contract validator. No hand-written stubs. |
| E2E | — | <!-- N/A: every flow in this feature is user-facing; full flows run as e2e-through-UI --> |
| Load | Shell load NFR only (first signed-in screen usable ≤ 2.5 s p75). | The load or performance tool already in your repo, or e.g. k6 or Locust. See §NFR validation. |
| Component | `AppShell` (desktop menu, phone bar, current-section marking, counter, footer, sign out), `StatusBanner`, `ThemeSwitch` (segmented and menu), `TimeZonePicker`, `Toast` with an action, and the pages SCR-10, -64, -69, -94, -95, -92, -93 in every state their `screens.md` rows list. | Render in isolation with API calls answered by in-test handlers. Assert copy from the message catalog, roles and `aria-*` attributes, focus, and busy, disabled and alert states. |
| Visual-regression | **Deferred** (review 2026-10-03, B7 → spec §8): no baselines exist yet; `aria-current` + bold weight and the "+N more" text are asserted by component and e2e tests meanwhile. Planned scope: the shell frame: desktop side menu, phone bottom bar, More sheet, a Status Banner (single and "+N more"), and the Coming soon page, each in light and dark at 360 px and 1280 px. | Snapshot the rendered frame with deterministic data (fixed email, fixed counter, fixed fonts) and fail on an unintended diff. Baselines change only in a deliberate update committed with the change that caused it. |
| E2E-through-UI | The `ux-flows.md` flows in a real browser against the full app started with the `e2e` profile and a throwaway Postgres. | Every test runs in both the phone (360 px) and desktop (1280 px) projects. Every shell screen it visits gets an accessibility scan in both themes and a no-sideways-scroll check. Network loss, unanswered requests, device timezone and device color scheme are emulated by the browser harness. Two Owners or two devices use separate browser contexts. |

## AC coverage

| AC (spec.md §5) | Test name (intent-based) | Level | Expected outcome |
|---|---|---|---|
| AC-170 | section registry lists the seven sections in app-map order | unit | Overview, Inbox, Chats, Assistants, Runs, Tasks, Settings, in that order, each with a path, label and icon |
| AC-170 | desktop menu shows every section and marks the current one by more than color | component | At 768 px or wider all seven items render; the current one has `aria-current="page"`, the inline-start bar and a bold label |
| AC-170 | signed-in Owner lands on the Inbox with the side menu | e2e-through-UI | Desktop: opening teleX shows SCR-10 with Inbox current in a menu of seven sections in app-map order |
| AC-43 | phone placement keeps at most five bar items with Inbox always in the bar | unit | The bar holds Inbox, Chats, Assistants, Tasks and More; Overview, Runs, Settings are under More; no registry entry can put Inbox under More |
| AC-43 | phone bar and More sheet render the split | component | Below 768 px: five bar items with the counter on Inbox; More opens a sheet with Overview, Runs, Settings, the theme switch and Sign out; a section under More marks More as current |
| AC-43 | Owner moves between sections on a phone | e2e-through-UI | Phone: each section is reached from the bar or in one tap through More; Inbox and its counter stay in the bar on every screen; every bar target is at least 44 × 44 px |
| AC-07b | no shell screen scrolls sideways at 360 px in either theme | e2e-through-UI | Each section, More, Settings, Profile and security, Coming soon and a screen with a Status Banner showing: page width never exceeds the viewport, in light and dark |
| AC-171 | Coming soon page names the section and offers the way back | component | SCR-94 shows the section name as h1, the "Coming soon" badge with icon, the one-sentence description from the catalog and "Go to Inbox" |
| AC-171 | an unbuilt section opens Coming soon inside the shell | e2e-through-UI | Opening Runs (by menu and by direct link) shows SCR-94 with Runs marked current; "Go to Inbox" opens SCR-10 |
| AC-172 | Settings lists its subsections | component | SCR-69 shows one row, Profile and security, linking to SCR-64 |
| AC-172 | Settings reaches Profile and security, and Sign out ends the session | e2e-through-UI | Both widths: Settings → Profile and security in one step; Sign out (sidebar footer on desktop, More on phone) shows the sign-in page, and going back shows nothing of the shell |
| AC-173 | new shell endpoints refuse a visitor without a live session | integration | Pulse, me, preferences, detected timezone and timezone list are refused as unauthenticated without a session, and as session ended for an ended one; nothing of the Owner's data is returned |
| AC-173 | the background pulse never extends a Sign-in Session | integration | A session's last-activity time is unchanged after pulses marked as background |
| AC-173 | refusal bodies match the agreed problem shape | contract | The "not signed in" and "session ended" answers of every new endpoint validate against `openapi.yaml` |
| AC-173 | a brand-new account goes to the remembered section after the passkey step | unit | Sign-in for a new account leaves the destination in place; the passkey step (created or skipped) takes it; an existing account takes it at sign-in; no destination → Inbox |
| AC-173 | the shell shows nothing until a live session is confirmed, and drops at once when it ends | component | While `me` is in flight only the loading state shows (no sections, counter or banner); a pulse answered as session ended clears the shell and shows SCR-92 |
| AC-173 | signed-out link to a section returns there after sign-in | e2e-through-UI | Opening a Runs link signed out shows SCR-01; after a Sign-in Code in the same browser the Owner lands on Runs (Coming soon) |
| AC-173 | new account from a section link sees the passkey offer first | e2e-through-UI | Runs link → sign up → SCR-09 → skip or create → Runs |
| AC-173 | Sign-in Link opened in another browser lands on the Inbox | e2e-through-UI | The link opened in a second browser context signs in there and lands on SCR-10, not on the remembered section |
| AC-173 | a session ended elsewhere ends the open tab | e2e-through-UI | After the session is ended from another browser, the open tab's next pulse or action shows SCR-92 and nothing of the shell |
| AC-174 | counter shows no number at zero, the count up to 99 and "99+" above | unit | 0 → no number; 1 and 99 → the number; 100 → "99+" |
| AC-174 | pulse returns the sum of every Inbox source for the Owner | integration | With two fixture sources answering 2 and 1, the pulse reports 3; after one changes, the next pulse reports the new sum |
| AC-174 | pulse body matches the agreed shape | contract | The pulse answer (count and condition codes) validates against `openapi.yaml` |
| AC-174 | counter states render with an accessible label | component | No number at 0 (item kept), the pill with "{n} items need you", "99+" with "More than 99 items need you"; a change is announced once |
| AC-174 | counter changes live without a reload | e2e-through-UI | Fixture count 3 → 4 → 2 → 0 → 120: the screen shows 4, 2, no number, "99+", each within 5 s, with no page reload, on any section |
| AC-175 | each Owner's pulse counts only that Owner's items | integration | Fixture sources hold 5 items for Owner A and none for Owner B; B's pulse reports 0 and each source was asked only for B's id |
| AC-175 | an Owner with an empty Inbox sees no number while another has five | e2e-through-UI | Two contexts side by side: A shows 5, B shows no number; signing B out and A in within the same browser never shows B's or A's count to the other |
| AC-176 | connectivity follows the network state and teleX's answers | unit | Browser offline → offline; no answer in time, a network error or a proxy gateway error → not responding; an answered pulse → online; an answered server failure leaves connectivity online and is routed to SCR-93 |
| AC-176 | the fetch client keeps the screen when teleX doesn't answer | unit | A call with no answer in 10 s no longer opens SCR-93; it feeds connectivity (the narrowed E01 AC-102) |
| AC-176 | offline and not-responding banners show an icon, words and Try again | component | Under the header: the wifi-off icon with "You're offline…" or the cloud-off icon with "teleX isn't responding.", `role="status"`, a "Try again" button |
| AC-176 | banner appears and clears with the connection, the screen kept | e2e-through-UI | Network cut: "You're offline" within 5 s; answers aborted with the network up: "teleX isn't responding" within 5 s; the screen content stays; restored: the banner is gone within 5 s and the screen shows fresh data |
| AC-176 | an answered failure still opens "teleX is unavailable" | e2e-through-UI | An action answered with a server failure shows SCR-93 with Retry, not the banner |
| AC-177 | Try again while still down says it keeps retrying and clears nothing | component | Try again turns busy ("Trying again"); after a failed pulse the text becomes "Still can't reach teleX. It keeps trying on its own." and the page content is unchanged |
| AC-177 | Try again during an outage keeps the banner and the screen | e2e-through-UI | With the network cut, pressing Try again keeps the banner with the still-down text; what was on screen stays; restoring the network clears it on its own |
| AC-178 | the most important condition wins, the rest are counted | unit | With offline plus two fixture codes, offline is chosen by the fixed order and "2 more" lists the others; an unknown code is dropped and logged once |
| AC-178 | pulse reports server-side condition codes for the Owner | integration | A fixture condition source's codes for the Owner appear in the pulse; another Owner's codes don't |
| AC-178 | banner has no close control and expands "N more" with each action | component | No close button in any banner state; "+{n} more" toggles `aria-expanded` and lists each condition with its icon, words and own action |
| AC-178 | banner can't be dismissed while its cause holds, and several show as one | e2e-through-UI | A fixture condition plus a network cut: one banner (the more important), "+1 more" lists the other; Escape or any other gesture doesn't hide it; it goes only when the cause ends |
| AC-179 | changing the theme saves it on the account | integration | After a theme change the Owner's stored theme is dark; a later `me` returns dark; a value outside light, dark and system is refused as a field error and the stored theme is unchanged |
| AC-179 | preferences change matches the agreed shape | contract | The preferences change request and answer, and the `me` body with theme and timezone, validate against `openapi.yaml` |
| AC-179 | theme switch applies at once and sends the change | component | Choosing Dark sets the dark theme attribute immediately, remembers it on this device, sends the change, and marks Dark as checked in every switch |
| AC-179 | choosing Dark switches every screen without a reload | e2e-through-UI | On SCR-64 the theme attribute is dark within 200 ms of the click, no navigation happens, and after a reload the screen opens dark |
| AC-180 | System resolves from the device's color scheme | unit | System with a dark device scheme → dark; a scheme change event → light; Light or Dark ignore the scheme |
| AC-180 | System follows the device while teleX is open | e2e-through-UI | With System saved, flipping the emulated device scheme switches the theme live, with no reload; the switch stays on System |
| AC-181 | first paint uses the theme last used on this device, at most one switch follows | unit | Remembered light + account dark → first paint light, then exactly one switch to dark; nothing remembered → the account theme applies on the first signed-in screen |
| AC-181 | the account theme follows the Owner to a new device | e2e-through-UI | Laptop context chooses Dark; a fresh phone context signs in and its first signed-in screen is dark |
| AC-181 | a device used before shows its last theme, then switches once | e2e-through-UI | A context that remembers light, with the account now dark: the theme attribute is light at first paint, then switches once to dark after `me`; an already open context keeps its theme until reloaded |
| AC-182 | failed theme save reverts and offers Try again | component | When the change gets no answer or is refused, the previous theme is reapplied everywhere, the device memory reverts, and an error toast "Your theme wasn't saved." carries "Try again" |
| AC-182 | theme change while offline is reverted with a message | e2e-through-UI | With the save request aborted, choosing Dark shows dark, then returns to the previous theme with the toast; Try again after recovery saves it |
| AC-183 | device timezone is used, with UTC as the fallback | unit | `Europe/Kyiv` → saved as is, no fallback; an unreadable value or one not on the list → `UTC` with the fallback flag |
| AC-183 | the first timezone save happens only while none is saved | integration | An Owner with no timezone gets Kyiv saved; a second "detected" save from another device (New York) changes nothing and returns Kyiv; two concurrent first saves leave exactly one value; a fallback flag is only ever stored with UTC |
| AC-183 | detected-timezone save matches the agreed shape | contract | The request and the answer (with the saved zone and fallback flag) validate against `openapi.yaml` |
| AC-183 | Profile and security shows the timezone, the hint and dates in it | component | The Time zone card shows the zone and offset; the "pick your own" hint shows only with the fallback flag; session and passkey dates render in the saved zone |
| AC-183 | first open saves the device timezone and another device doesn't change it | e2e-through-UI | A context in Kyiv opens teleX: SCR-64 shows Kyiv and dates in Kyiv time; a context in New York signs in later and SCR-64 still shows Kyiv |
| AC-183 | an unreadable device timezone falls back to UTC with a hint | e2e-through-UI | A context whose device zone isn't on the list: SCR-64 shows UTC and the hint "We couldn't read your device's time zone…" on every device until a zone is picked |
| AC-184 | the known list holds `Area/City` names and UTC | unit | `Europe/Kyiv` and `UTC` are known; aliases outside `Area/City` and made-up names are not |
| AC-184 | picking a timezone saves it and clears the fallback | integration | A pick from the list is stored, the fallback flag becomes false, and the Owner's timezone read by other modules returns the new zone |
| AC-184 | timezone list matches the agreed shape | contract | The timezone list answer validates against `openapi.yaml` |
| AC-184 | the picker searches by city or region | component | Typing "kyi" or "Europe" narrows the list; arrow keys and Enter pick; the current zone is checked |
| AC-184 | a picked timezone is used for every date shown | e2e-through-UI | Picking New York on SCR-64 shows "Time zone saved.", the card and every date on the page re-render in New York time, and a second context opened afterwards shows New York |
| AC-185 | no-match search says so and suggests a nearby city | component | A query matching nothing shows "No time zone matches “{query}”. Try a nearby city." and the current zone stays checked |
| AC-185 | a timezone not on the list is refused | integration | A change to a zone outside the known list is refused as a field error on the timezone, and the stored zone is unchanged |
| AC-186 | an empty timezone is refused and the current one kept | integration | A change to an empty timezone for an Owner with a saved zone is refused as a field error on the timezone; the stored zone is unchanged; no code path writes an empty zone once one is set |
| AC-186 | the picker offers no empty choice | component | The picker has no "none" option and closing it without a pick leaves the zone unchanged; a server refusal shows "Choose a time zone from the list." under the card |

## Edge cases / error paths

Each error and authorization AC (AC-173, AC-175, AC-177, AC-182, AC-185, AC-186) has its own rows above. These are the further boundary and failure cases the spec, `sad.md` and `screens.md` imply:

- Counter at exactly 99 and 100 → "99" and "99+" (unit, inside the AC-174 row's cases).
- A pulse answered after more than 2 s while the network is up → counted as not responding, and the next answered pulse clears it (unit, connectivity; the tight 5 s budget risk in sad §11).
- A pulse fails while the tab is hidden, then the tab becomes visible → a pulse fires at once, and the counter and banner are fresh within 5 s (e2e-through-UI, visibility emulated).
- A lazily loaded section can't download while offline → the content area shows "This section didn't load." with "Try again", and the navigation and banner stay (component + e2e-through-UI).
- Timezone list doesn't load → "The time zone list didn't load." with "Try again" (component).
- Timezone save gets no answer (no spec AC; drawn as `tz-save-failed` in screens.md) → the current zone stays, an error toast "Your time zone wasn't saved." carries "Try again", and the banner shows (component). The gap is noted in sad §6 Flags as a candidate AC.
- An unknown Status Banner code in the pulse → not shown, logged once (unit, in the AC-178 row).
- Two tabs in one browser → a theme change in one is applied in the other through the shared device memory (unit, theme sync).
- Crossing 768 px while More is open → the sheet closes and the side menu shows (component).
- A theme outside light, dark and system → refused as a field error (integration, in the AC-179 row).
- A stored fallback flag with a zone other than UTC → rejected by the database constraint (integration).
- Migration applies, rolls back and re-applies cleanly; existing Owners get theme System and no timezone (integration, the existing up → down → up suite).
- `inbox` depends only on `shared`, `web` may depend on `inbox`, and `shared` holds no beans (integration, the existing module-boundary check).
- No production configuration enables the `e2e` profile, and the fixture endpoint changes only the calling Owner's fixture values (integration; also checked at `/sdd:review`, sad §11).
- E01 tests that assert the old AC-102 behavior (no answer → SCR-93) and the old new-account landing (straight to the Inbox) are updated in the same task that changes the behavior, not deleted (sad §11).

## Test data

- Seed strategy: Kotlin builders from `data-model.md` §Test fixtures. `anOwner(...)` defaults to theme System and no timezone (the AC-183 starting state). `anOwnerWithTimeZone("Europe/Kyiv")` is used for AC-184…AC-186, and `anOwnerOnUtcFallback()` for the hint. Inbox counts and Status Banner conditions are set through the `e2e`-profile fixture sources and their fixture endpoint, per Owner, not as rows. Emails use `example.test` only.
- Integration dependency: a throwaway Postgres container (the repo's pgvector image), started once per suite run through the existing shared configuration. No mocked store.
- Cleanup boundary: per test. Each test creates its own Owner(s) with fresh ids and asserts only on them, so tests don't depend on order. Fixture source values are keyed by Owner id and reset per test. e2e-through-UI tests sign up a fresh Owner per test, use a new browser context per device, and clear device memory (remembered theme and destination) between tests.

## NFR validation (load)

The shell-load NFR is the one throughput-style budget, so it gets its own scenario:

- **Shell load ≤ 2.5 s p75 (phone profile, fast-4G):** 20 cold opens of a signed-in Owner on the phone profile with the network throttled to fast 4G, a fresh cache each time. Measure from navigation start until SCR-10 and its counter are interactive. Assert p75 ≤ 2.5 s. Use the performance tool already in your repo, or e.g. k6 or Locust.
- **Deliberate deviation (review 2026-10-03-2, R2-12):** what is built is `e2e/tests/shell-sweep.spec.ts`, which runs on every PR in both projects with **5** cold opens (fast-4G throttling over CDP, cache disabled), measured in the page with `performance.now()` from navigation start to the counter's first render, and asserts p75 < 2.5 s with no headroom. It is a per-PR early warning against regressions, not the release measurement: with 5 samples the p75 is the 4th value. The 20-open scheduled phone-only run is not built yet; until it is, the manual browser pass before `/sdd:ship` records a phone load time in the PR.

The other numeric §6 targets are timing and layout budgets, not load. They are asserted inside the e2e-through-UI rows above, on both profiles:

- Offline banner appears ≤ 5 s, clears ≤ 5 s (AC-176 rows).
- Inbox counter freshness ≤ 5 s (AC-174 row).
- Theme applied ≤ 200 ms with no reload; on a used device, first paint shows the last theme there and at most one switch follows (AC-179, AC-181 rows).
- 0 shell screens wider than the viewport at 360 px and 1280 px, both themes (AC-07b row, plus the same check at 1280 px in every desktop test).
- 0 serious or critical accessibility-scan findings on every shell screen, both widths and both themes; bottom-bar targets ≥ 44 × 44 px; WCAG 2.2 AA contrast (every e2e-through-UI test, plus the AC-43 row).
- 100 % of UI e2e scenarios run in both the phone and desktop projects: a check over the CI report fails if a scenario ran in only one project.

Not automated: "latest two versions of Chrome and Safari, including Safari on iOS" is a manual pass before `/sdd:ship`, recorded in the PR (spec §6). It covers the theme at first paint, System following, the pulse pausing and resuming when the tab is hidden on iOS, and the offline banner. No pulse-throughput scenario is added: spec §6 has no throughput number, and sad §7 watches pulse p95 through metrics instead.

## CI placement

- On every PR: unit, component, contract, integration (with its throwaway container), and the module-boundary and migration checks. These are the existing `build` and `integrationTest` runs.
- On every PR as well, because spec §6 requires both widths from E06 on: e2e-through-UI in both projects with the accessibility scan, width and target checks, plus the 5-open shell-load check (see §NFR validation). Visual regression is deferred (spec §8).
- On a schedule and before release (not built yet, see §NFR validation): the 20-open shell-load scenario with throttling (it's slow and noisy on shared runners), and the manual browser pass before `/sdd:ship`.
