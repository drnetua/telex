# Tracker — platform-skeleton

> Status of every task in the epic. `implement` updates `done` as it commits each task.
> States: `todo` · `in_progress` · `blocked` · `review` · `done`.

| # | Task | Layer | Owner | Estimate | Blocked by | Status |
|---|---|---|---|---|---|---|
| T1 | Promote the four staged identity migrations into the live Flyway tree | migration | Anton Husiev | S | — | todo |
| T2 | Add identity domain primitives: typed ids, Clock, email canonicalisation, secrets, device naming and the public URL | domain | Anton Husiev | M | — | todo |
| T3 | Create the mail integration module with the Mailer port and the SMTP adapter | wiring | Anton Husiev | S | — | todo |
| T4 | Build the Owner and Sign-in Session core: start, resolve with the 30/90-day rules, end by key, and the SignInSessionStarted event | infra | Anton Husiev | M | T1, T2 | todo |
| T5 | Issue a Sign-in Grant with its email and read a Sign-in Link without redeeming it | app | Anton Husiev | M | T1, T2, T3 | todo |
| T6 | Redeem a Sign-in Link or Sign-in Code atomically and start the Sign-in Session | app | Anton Husiev | M | T4, T5 | todo |
| T7 | Send the "New sign-in to teleX" email from the SignInSessionStarted event after commit | app | Anton Husiev | S | T3, T4 | todo |
| T8 | Add the Spring Security filter chain with the session cookie, CSRF and no-store, and switch problem codes to kebab-case | wiring | Anton Husiev | M | T4 | todo |
| T9 | Expose the sign-in REST endpoints: request email, preview and redeem link, redeem code, sign out | ports | Anton Husiev | M | T5, T6, T8 | todo |
| T10 | List and end my Sign-in Sessions and answer "who am I", Owner-scoped end to end | ports | Anton Husiev | M | T4, T8 | todo |
| T11 | Wire Spring Security WebAuthn for passkey registration and passkey sign-in into the one session mechanism | wiring | Anton Husiev | M | T8 | todo |
| T12 | List and remove my Passkeys, filtered by my WebAuthn user entity | ports | Anton Husiev | S | T11 | todo |
| T13 | Set up the SPA router, query client and fetch client with failure routing, and build the system pages SCR-91, SCR-92 and SCR-93 | ui | Anton Husiev | M | — | todo |
| T14 | Build Sign in (SCR-01, email part) and Check your email (SCR-07) with the Sign-in Code | ui | Anton Husiev | M | T13 | todo |
| T15 | Build Confirm sign-in link (SCR-08) and the landing-after-sign-in rule | ui | Anton Husiev | S | T14 | todo |
| T16 | Build Create a passkey (SCR-09) and "Sign in with a passkey" on SCR-01 | ui | Anton Husiev | M | T15 | todo |
| T17 | Build the signed-in PageFrame with Sign out and the empty Inbox (SCR-10) | ui | Anton Husiev | S | T13 | todo |
| T18 | Build Profile and security (SCR-64): passkeys card and sign-in sessions card | ui | Anton Husiev | M | T16, T17 | todo |
| T19 | Make teleX start with one command: Dockerfile, compose with app and Mailpit, README and a smoke script | docs | Anton Husiev | M | T9, T14 | todo |
| T20 | Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator | tests | Anton Husiev | M | T7, T10, T12, T15, T16, T18, T19 | todo |

**Total:** 20 tasks, ~17 person-days (S = ½ day, M = 1 day).
