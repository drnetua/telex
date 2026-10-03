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

**Total:** 38 tasks — T1–T25 from the breakdown, T26–T38 follow-ups from review 2026-10-03.
