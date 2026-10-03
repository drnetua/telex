# Tracker — app-shell

> Status of every task in the epic. `implement` updates `done` as it commits each task.
> States: `todo` · `in_progress` · `blocked` · `review` · `done`.

| # | Task | Layer | Owner | Estimate | Blocked by | Status |
|---|---|---|---|---|---|---|
| T1 | Promote the staged owner-preferences migration into the live Flyway tree | migration | Anton Husiev | S | — | done |
| T2 | Add the Theme type and the known timezone list to identity | domain | Anton Husiev | S | — | done |
| T3 | Read and change the Owner's theme and timezone in identity, with the save-if-unset write | infra | Anton Husiev | M | T1, T2 | todo |
| T4 | Create the inbox module (InboxSource + sum) and the shared StatusConditionSource contract | wiring | Anton Husiev | S | — | todo |
| T5 | Serve me with preferences, PATCH /me/preferences, the detected-timezone save and GET /time-zones | ports | Anton Husiev | M | T3 | todo |
| T6 | Serve GET /api/v1/pulse and the e2e-profile fixture Inbox and condition sources | ports | Anton Husiev | M | T4 | todo |
| T7 | Add the connectivity state and the 3 s pulse query, and narrow fetch-client failure routing | ui | Anton Husiev | M | — | todo |
| T8 | Apply the theme before first paint, follow System, sync tabs and switch once to the account theme | ui | Anton Husiev | S | — | todo |
| T9 | Build ThemeSwitch (segmented and menu), the Toast action prop and the Appearance card on SCR-64 | ui | Anton Husiev | M | T8 | todo |
| T10 | Port AppShell (C-01) from the section registry: side menu, phone bottom bar, More sheet and Sign out | ui | Anton Husiev | L | T9 | todo |
| T11 | Route every section lazily, with the Coming soon page (SCR-94) and the Settings page (SCR-69) | ui | Anton Husiev | M | T10 | todo |
| T12 | Show the live Inbox counter in the shell from the pulse (none at 0, 99+ above 99) | ui | Anton Husiev | S | T7, T10 | todo |
| T13 | Port StatusBanner (C-04) with the condition catalog, Try again, still-down and N more | ui | Anton Husiev | M | T7, T10 | todo |
| T14 | Save the device timezone on first open and show SCR-64 dates in the Owner's timezone | ui | Anton Husiev | S | T9, T10 | todo |
| T15 | Build the Time zone card with the fallback hint and the searchable TimeZonePicker on SCR-64 | ui | Anton Husiev | M | T14 | todo |
| T16 | Land a brand-new account on the remembered section after the passkey step | ui | Anton Husiev | S | — | todo |
| T17 | e2e: navigation on both widths, return after sign-in, and the axe / width / target / load sweep | tests | Anton Husiev | M | T5, T6, T11, T13, T16 | todo |
| T18 | e2e: live Inbox counter and Status Banners, with timing, and the narrowed AC-102 | tests | Anton Husiev | M | T6, T12, T13, T17 | todo |
| T19 | e2e: theme choice, System follow, cross-device first paint, save failure, and the timezone flows | tests | Anton Husiev | M | T5, T15, T17 | todo |

**Total:** 19 tasks, ~13 person-days (S ≈ 0.5 d, M ≈ 0.75 d, L ≈ 1 d).
