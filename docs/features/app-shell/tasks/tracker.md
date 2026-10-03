# Tracker — app-shell

> Status of every task in the epic. `implement` updates `done` as it commits each task.
> States: `todo` · `in_progress` · `blocked` · `review` · `done`.

| # | Task | Layer | Owner | Estimate | Blocked by | Status |
|---|---|---|---|---|---|---|
| T1 | Promote the staged owner-preferences migration into the live Flyway tree | migration | Anton Husiev | S | — | done |
| T2 | Add the Theme type and the known timezone list to identity | domain | Anton Husiev | S | — | done |
| T3 | Read and change the Owner's theme and timezone in identity, with the save-if-unset write | infra | Anton Husiev | M | T1, T2 | done |
| T4 | Create the inbox module (InboxSource + sum) and the shared StatusConditionSource contract | wiring | Anton Husiev | S | — | done |
| T5 | Serve me with preferences, PATCH /me/preferences, the detected-timezone save and GET /time-zones | ports | Anton Husiev | M | T3 | done |
| T6 | Serve GET /api/v1/pulse and the e2e-profile fixture Inbox and condition sources | ports | Anton Husiev | M | T4 | done |
| T7 | Add the connectivity state and the 3 s pulse query, and narrow fetch-client failure routing | ui | Anton Husiev | M | — | done |
| T8 | Apply the theme before first paint, follow System, sync tabs and switch once to the account theme | ui | Anton Husiev | S | — | done |
| T9 | Build ThemeSwitch (segmented and menu), the Toast action prop and the Appearance card on SCR-64 | ui | Anton Husiev | M | T8 | done |
| T10 | Port AppShell (C-01) from the section registry: side menu, phone bottom bar, More sheet and Sign out | ui | Anton Husiev | L | T9 | done |
| T11 | Route every section lazily, with the Coming soon page (SCR-94) and the Settings page (SCR-69) | ui | Anton Husiev | M | T10 | done |
| T12 | Show the live Inbox counter in the shell from the pulse (none at 0, 99+ above 99) | ui | Anton Husiev | S | T7, T10 | done |
| T13 | Port StatusBanner (C-04) with the condition catalog, Try again, still-down and N more | ui | Anton Husiev | M | T7, T10 | done |
| T14 | Save the device timezone on first open and show SCR-64 dates in the Owner's timezone | ui | Anton Husiev | S | T9, T10 | done |
| T15 | Build the Time zone card with the fallback hint and the searchable TimeZonePicker on SCR-64 | ui | Anton Husiev | M | T14 | done |
| T16 | Land a brand-new account on the remembered section after the passkey step | ui | Anton Husiev | S | — | done |
| T17 | e2e: navigation on both widths, return after sign-in, and the axe / width / target / load sweep | tests | Anton Husiev | M | T5, T6, T11, T13, T16 | done |
| T18 | e2e: live Inbox counter and Status Banners, with timing, and the narrowed AC-102 | tests | Anton Husiev | M | T6, T12, T13, T17 | done |
| T19 | e2e: theme choice, System follow, cross-device first paint, save failure, and the timezone flows | tests | Anton Husiev | M | T5, T15, T17 | done |
| T20 | Make the preferences and pulse endpoints honour the contract: nullable timeZone, refused unknown keys, applied condition pattern, one-query /me | ports | Anton Husiev | S | — | done |
| T21 | Keep one shared theme state: applied choice, save sequence, pending, revert and the failed-save toast, and route 401/5xx preference-save failures | ui | Anton Husiev | M | — | done |
| T22 | Make the theme switches keyboard-correct: one radio group per switch and a real menu keyboard pattern | ui | Anton Husiev | S | T21 | done |
| T23 | Remember the section on Session ended, clear the previous Owner's cache on sign-in, and keep a pulse 5xx on the banner | ui | Anton Husiev | S | — | done |
| T24 | Fix More sheet focus (no re-steal, trapped Tab), announce the first Status Banner, and keep sticky toasts off the phone bar | ui | Anton Husiev | S | T21 | done |
| T25 | Build the Time zone card's tz-not-yet state, return focus after a pick, and replace the tautological me test | ui | Anton Husiev | S | T21 | done |
| T26 | e2e: tighten the shell and preference proofs: stable narrowed AC-102, fresh data after recovery, another browser, A/B in one browser, any-section counter, theme/zone reload checks, in-page load timing | tests | Anton Husiev | M | T20, T22, T23, T24, T25 | done |
| T27 | Bring the docs in line: ux-flows platform decision, Coming soon copy and settled open questions, inventory line refs, raw-UUID deviation | docs | Anton Husiev | S | T20, T22, T24, T25 | done |

**Total:** 27 tasks (T20–T27 are review follow-ups from `_review/review-2026-10-03.md`), ~18 person-days (S ≈ 0.5 d, M ≈ 0.75 d, L ≈ 1 d).
