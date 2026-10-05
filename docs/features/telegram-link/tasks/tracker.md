# Tracker — telegram-link

> Status of every task in the epic. `implement` updates `done` as it commits each task.
> States: `todo` · `in_progress` · `blocked` · `review` · `done`.

| # | Task | Layer | Owner | Estimate | Blocked by | Status |
|---|---|---|---|---|---|---|
| T1 | Prove TDLight on JDK 25 and land the binding behind the TdlibFacade (spike) | infra | Anton Husiev | M | — | done |
| T2 | Promote the owner_key, linked_account and channel migrations into the live Flyway tree | migration | Anton Husiev | S | — | done |
| T3 | Add OwnerKeys envelope encryption with the master-key check and reset, and SignInSessions.isLive | app | Anton Husiev | M | T2 | done |
| T4 | Define the TelegramSessions port, its in-process events and the fake Telegram adapter | infra | Anton Husiev | M | — | done |
| T5 | Manage Telegram session directories: per-session dir, destroy with one-minute retry, startup orphan sweep | infra | Anton Husiev | S | T4 | done |
| T6 | Implement the tdlight adapter: TDLib auth states, errors and chat list mapped to the port | infra | Anton Husiev | M | T1, T5 | done |
| T7 | Build the LinkedAccount aggregate: rules, states, masked phone, repository, events and the account limit | domain | Anton Husiev | M | T2 | done |
| T8 | Start, resume, cancel and expire the in-memory linking attempt (one per Owner) | app | Anton Husiev | M | T3, T4, T7 | done |
| T9 | Run the phone, code, resend and password steps with Telegram's refusals and waits | app | Anton Husiev | M | T8 | done |
| T10 | Complete an authorized attempt: link a new account, sign in again, or refuse and log out | app | Anton Husiev | M | T5, T9 | done |
| T11 | Unlink an account: bounded sign-out, one-transaction delete with AccountUnlinked, then destroy the session | app | Anton Husiev | S | T5, T7 | done |
| T12 | Reconnect accounts on boot and follow Telegram's session state (Connected, Reconnecting, Session lost) | app | Anton Husiev | M | T3, T5, T7 | done |
| T13 | Sync each account's chat list into channel rows with throttled progress events | app | Anton Husiev | M | T4, T7 | done |
| T14 | Expose list and unlink of my Linked Accounts, and back getMe.linkedAccountCount with messaging | ports | Anton Husiev | S | T7, T11 | done |
| T15 | Expose the linking-attempt endpoints with the contract's problem codes, validation and Retry-After | ports | Anton Husiev | M | T10 | done |
| T16 | Serve the live-update SSE stream of invalidation hints per Owner | ports | Anton Husiev | M | T7 | done |
| T17 | Wire the Operator config, the session volume and the README Telegram setup step | wiring | Anton Husiev | S | T3, T6 | done |
| T18 | Add the SPA API clients, the single SSE live-update client and all new copy | ui | Anton Husiev | M | — | done |
| T19 | Build LinkedAccountSummary, port StatusBanner, extend CodeInput, Icon and PageFrame | ui | Anton Husiev | M | T18 | done |
| T20 | Build the SCR-02 wizard steps: phone, code, password with their validation and refusals | ui | Anton Husiev | M | T19 | done |
| T21 | Build the SCR-02 outcomes: wait countdown, refusals, attempt ended, cancel and success | ui | Anton Husiev | M | T20 | done |
| T22 | Make SCR-10 Inbox start linking and list one line per Linked Account | ui | Anton Husiev | S | T19 | done |
| T23 | Build SCR-60 Accounts: the list with states, Add account, Sign in again and the unlink dialog | ui | Anton Husiev | M | T19 | done |
| T24 | Prove nothing is left behind or leaked: unlink dump, restart delivery, registry and log scans | tests | Anton Husiev | M | T10, T11, T12, T13, T16 | done |
| T25 | Add Playwright end-to-end tests of linking, live state and unlink at 360 px and 1280 px with axe | tests | Anton Husiev | M | T14, T15, T17, T21, T22, T23 | done |
| T26 | Publish sync progress inside a transaction, write the sync time from the Clock, time the sync once | app | Anton Husiev | S | — | done |
| T27 | Start chat sync only after the Linked Account is committed, report Telegram's real total, drop chats left meanwhile | app | Anton Husiev | M | T26 | done |
| T28 | Unlink: report an unconfirmed sign-out for Session lost, and sign out the session the delete actually removed | app | Anton Husiev | S | T27 | done |
| T29 | Linking refusals: end the attempt on an unregistered number, 503 when Telegram does not answer at start, clear codeLength, whitespace phone is 422 | app | Anton Husiev | M | T28, T34 | done |
| T30 | Log out sign-ins Telegram authorized but teleX did not finish, and never hang on a failed client open | infra | Anton Husiev | M | T29 | done |
| T31 | Make the SSE emitter registry add and remove atomic per Owner | ports | Anton Husiev | S | — | done |
| T32 | Cover the remaining contract responses over HTTP, run completions concurrently, and restart the app for real | tests | Anton Husiev | M | T30, T31 | done |
| T33 | Bring sad §6/§7, the api-sync open questions and the screens.md registry in line with the code | docs | Anton Husiev | S | T30, T36 | done |
| T34 | SPA failures: domain 503 refusals stay on the page, SCR-02 never throws in render, step errors always give feedback, cache the new account | ui | Anton Husiev | M | — | done |
| T35 | SCR-60 and the banner: linked Toast, Reconnecting and Session lost notes, banner refusals, unlink focus, one-shot arrival Toast, icon and copy fixes | ui | Anton Husiev | M | T34, T29 | done |
| T36 | Render SCR-02 in the confirmed onboarding card layout | ui | Anton Husiev | S | T35 | done |
| T37 | Reopen the live-update stream when the browser has closed it, with capped backoff | ui | Anton Husiev | S | — | done |
| T38 | End-to-end: complete Sign in again, Reconnecting to Connected, banner goes away, AC-119, password and phone refusals | tests | Anton Husiev | M | T27, T29, T30, T35, T36, T37 | done |
| T39 | Sign in again racing an unlink never reports success on a deleted account or leaves an authorized session behind | app | Anton Husiev | M | — | done |
| T40 | Real adapter: logOut is false for a closed session, and the chat total ignores folder counts | infra | Anton Husiev | S | — | done |
| T41 | Unregistered number with no replacement session answers 422, and Resend ends the attempt on every phone refusal | app | Anton Husiev | S | T39 | done |
| T42 | Make the sync-in-transaction and AC-110 tests able to fail on their regressions | tests | Anton Husiev | S | — | done |
| T43 | Report Session lost through the shell's Status Banner, with the account name and a working Sign in again | ui | Anton Husiev | M | — | done |
| T44 | SPA polish: unlink focus, a dismissible SCR-02 load-failure Toast with Retry, one Toast slot, one refusal lookup, stronger tests | ui | Anton Husiev | M | T43 | done |
| T45 | Bring screens.md, sad §7, data-model, events.md, the contract and spec §8 in line with the code | docs | Anton Husiev | S | T39, T40, T41, T43, T44 | done |
| T46 | Say which phone refusal ended the attempt, and end a sign in again whose account was unlinked meanwhile as not found | app | Anton Husiev | M | — | done |
| T47 | Sign out a reopened session that has not reached Ready yet instead of skipping it | infra | Anton Husiev | S | — | done |
| T48 | SCR-02 survives a failed pulse, and a failed load shows an inline state with Try again and Back | ui | Anton Husiev | S | T46 | done |
| T49 | Banner: no false generic text, Sign in again outcome survives reorder and refetch, Toasts are announced | ui | Anton Husiev | M | — | done |
| T50 | Record the shell banner extension and the two live channels in the ADRs, and sync sad, the contract and screens.md | docs | Anton Husiev | S | T46, T47, T48, T49 | done |
| T51 | teleX's own sign-out on unlink is not a Session lost, and is confirmed only when Telegram finishes it | infra | Anton Husiev | M | — | done |
| T52 | An unlink during boot reopen leaves no live session, and the real resendCode reports phone refusals | infra | Anton Husiev | S | T51 | done |
| T53 | Banner Toast keeps its node, error Toasts are not nested live regions, a pulse 403 in the shell routes again, pulse tests cover 503/504 | ui | Anton Husiev | S | — | done |
| T54 | SCR-02 moves focus to the outcome card, Playwright covers the refusal-ended and load-failed states, and a 401 mid-wizard reaches SCR-92 | ui | Anton Husiev | S | — | done |
| T55 | Sync sad §7 tags, ux-flows, the sad coverage row and screens.md with the code, and record the third-review changes | docs | Anton Husiev | S | T51, T52, T53, T54 | done |
| T56 | A session whose unlink failed after teleX's own sign-out still reports its state, and the fake behaves the same | infra | Anton Husiev | S | — | done |
| T57 | An unlink during the boot reopen window still signs teleX out of Telegram, and a failed reopen leaves no directory | infra | Anton Husiev | M | T56 | done |
| T58 | The real code step ends the attempt on an invalid or banned number instead of answering 503 | infra | Anton Husiev | XS | T57 | done |
| T59 | SCR-02 moves focus back to the step after an outcome card, the wait card's buttons don't swap under focus, and the AC-110 and focus tests are sound | ui | Anton Husiev | S | — | done |
| T60 | Sync screens.md, events.md, the T54 record, sad, ux-flows and spec with the fourth-review fixes | docs | Anton Husiev | XS | T56, T57, T58, T59 | done |
| T61 | An unlink whose sealed key can't be opened still deletes the account and reports the sign-out unconfirmed | infra | Anton Husiev | XS | — | done |
| T62 | A failed delete after the sign-out leaves the account Session lost when Telegram confirmed it, and keeps an unconfirmed session running | infra | Anton Husiev | S | T61 | done |
| T63 | The fake reopens a destroyed session signed out, as TDLib does, and the boot-race test asserts the unconfirmed sign-out | infra | Anton Husiev | S | T62 | done |
| T64 | SCR-02 never focuses the step heading on the first load, and focuses it on every card-to-step change, including the banner's Sign in again | ui | Anton Husiev | XS | — | done |
| T65 | Sync sad, tracker, test-plan and spec with the fifth-review fixes | docs | Anton Husiev | XS | T61, T62, T63, T64 | done |
| T66 | Boot signs out a session reopened for an account unlinked meanwhile, and a unit test pins it | infra | Anton Husiev | XS | — | done |
| T67 | An interrupted log out unmutes the session, and closing it on an interrupted thread doesn't throw | infra | Anton Husiev | XS | — | done |
| T68 | SCR-02 does not focus the step heading when one step replaces another, and a test pins the guard | ui | Anton Husiev | XS | — | done |
| T69 | Sync sad, test-plan, ux-flows, task statuses and T53's AC with the sixth-review fixes | docs | Anton Husiev | XS | T66, T67, T68 | done |
| T70 | An unlink whose sign-out is interrupted still deletes the account and puts the interrupt back only at the end | app | Anton Husiev | XS | — | done |
| T71 | SCR-10 and SCR-60 each pin the start-refused toast for 503 telegram-unavailable | ui | Anton Husiev | XS | — | done |
| T72 | Correct the AC-121 citations and the interrupted-unlink claims after the seventh review | docs | Anton Husiev | XS | T70, T71 | done |
| T73 | Sign in again refreshes the account's masked phone along with its name | app | Anton Husiev | XS | — | done |
| T74 | An unlink deletes the account whatever way an interrupt arrives, and closes and destroys before handing it back | app | Anton Husiev | XS | — | done |
| T75 | The SCR-02 wait-card focus tests control the clock, and the SCR-10 503 test pins that no connection banner shows | ui | Anton Husiev | XS | — | done |
| T76 | Sync the data model, test plan and tracker with the eighth-review fixes | docs | Anton Husiev | XS | T73, T74, T75 | done |
| T77 | The SCR-10, SCR-60 and SCR-02 busy states each have a test of their label | ui | Anton Husiev | XS | — | done |
| T78 | An e2e test unlinks a connected account and sees the unlinked Toast and Connect Telegram | tests | Anton Husiev | XS | — | done |
| T79 | The SCR-02 wait card shows the retry time in the Owner's zone and counts down from Retry-After | ui | Anton Husiev | XS | — | done |
| T80 | The unlink dialog names the chat count in correct English, and leaves it out at zero | ui | Anton Husiev | XS | — | done |
| T81 | An unlink takes any pending interrupt just before the delete, including when there is no sign-out | app | Anton Husiev | XS | — | done |
| T82 | Sync the README, contract, screens, test plan and task files with the ninth-review fixes | docs | Anton Husiev | XS | T77, T78, T79, T80, T81 | done |
| T83 | The SCR-02 wait card announces the retry time in the Owner's zone, even when the zone arrives after the card | ui | Anton Husiev | XS | — | done |
| T84 | The SCR-02 code and password steps' submitting labels each have a test | tests | Anton Husiev | XS | — | done |
| T85 | Vitest runs in UTC, and a malformed Retry-After falls back to retryAt | tests | Anton Husiev | XS | — | done |
| T86 | Sync the test plan and task files with the tenth-review fixes, and file T78 under tests | docs | Anton Husiev | XS | T83, T84, T85 | done |

**Total:** 86 tasks — T1–T25 from the breakdown, T26–T38 follow-ups from review 2026-10-03, T39–T45, T46–T50, T51–T55, T56–T60, T61–T65, T66–T69, T70–T72 and T73–T76 follow-ups from the eight 2026-10-04 reviews, T77–T82 from the ninth 2026-10-05 review, and T83–T86 from the tenth.
