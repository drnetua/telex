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
| T9 | Run the phone, code, resend and password steps with Telegram's refusals and waits | app | Anton Husiev | M | T8 | todo |
| T10 | Complete an authorized attempt: link a new account, sign in again, or refuse and log out | app | Anton Husiev | M | T5, T9 | todo |
| T11 | Unlink an account: bounded sign-out, one-transaction delete with AccountUnlinked, then destroy the session | app | Anton Husiev | S | T5, T7 | todo |
| T12 | Reconnect accounts on boot and follow Telegram's session state (Connected, Reconnecting, Session lost) | app | Anton Husiev | M | T3, T5, T7 | done |
| T13 | Sync each account's chat list into channel rows with throttled progress events | app | Anton Husiev | M | T4, T7 | done |
| T14 | Expose list and unlink of my Linked Accounts, and back getMe.linkedAccountCount with messaging | ports | Anton Husiev | S | T7, T11 | todo |
| T15 | Expose the linking-attempt endpoints with the contract's problem codes, validation and Retry-After | ports | Anton Husiev | M | T10 | todo |
| T16 | Serve the live-update SSE stream of invalidation hints per Owner | ports | Anton Husiev | M | T7 | done |
| T17 | Wire the Operator config, the session volume and the README Telegram setup step | wiring | Anton Husiev | S | T3, T6 | todo |
| T18 | Add the SPA API clients, the single SSE live-update client and all new copy | ui | Anton Husiev | M | — | done |
| T19 | Build LinkedAccountSummary, port StatusBanner, extend CodeInput, Icon and PageFrame | ui | Anton Husiev | M | T18 | done |
| T20 | Build the SCR-02 wizard steps: phone, code, password with their validation and refusals | ui | Anton Husiev | M | T19 | done |
| T21 | Build the SCR-02 outcomes: wait countdown, refusals, attempt ended, cancel and success | ui | Anton Husiev | M | T20 | done |
| T22 | Make SCR-10 Inbox start linking and list one line per Linked Account | ui | Anton Husiev | S | T19 | done |
| T23 | Build SCR-60 Accounts: the list with states, Add account, Sign in again and the unlink dialog | ui | Anton Husiev | M | T19 | done |
| T24 | Prove nothing is left behind or leaked: unlink dump, restart delivery, registry and log scans | tests | Anton Husiev | M | T10, T11, T12, T13, T16 | todo |
| T25 | Add Playwright end-to-end tests of linking, live state and unlink at 360 px and 1280 px with axe | tests | Anton Husiev | M | T14, T15, T17, T21, T22, T23 | todo |

**Total:** 25 tasks, ~22 person-days (S = ½ day, M/L = 1 day).
