# Tracker — platform-skeleton

> Status of every task in the epic. `implement` updates `done` as it commits each task.
> States: `todo` · `in_progress` · `blocked` · `review` · `done`.

| # | Task | Layer | Owner | Estimate | Blocked by | Status |
|---|---|---|---|---|---|---|
| T1 | Promote the four staged identity migrations into the live Flyway tree | migration | Anton Husiev | S | — | done |
| T2 | Add identity domain primitives: typed ids, Clock, email canonicalisation, secrets, device naming and the public URL | domain | Anton Husiev | M | — | done |
| T3 | Create the mail integration module with the Mailer port and the SMTP adapter | wiring | Anton Husiev | S | — | done |
| T4 | Build the Owner and Sign-in Session core: start, resolve with the 30/90-day rules, end by key, and the SignInSessionStarted event | infra | Anton Husiev | M | T1, T2 | done |
| T5 | Issue a Sign-in Grant with its email and read a Sign-in Link without redeeming it | app | Anton Husiev | M | T1, T2, T3 | done |
| T6 | Redeem a Sign-in Link or Sign-in Code atomically and start the Sign-in Session | app | Anton Husiev | M | T4, T5 | done |
| T7 | Send the "New sign-in to teleX" email from the SignInSessionStarted event after commit | app | Anton Husiev | S | T3, T4 | done |
| T8 | Add the Spring Security filter chain with the session cookie, CSRF and no-store, and switch problem codes to kebab-case | wiring | Anton Husiev | M | T4 | done |
| T9 | Expose the sign-in REST endpoints: request email, preview and redeem link, redeem code, sign out | ports | Anton Husiev | M | T5, T6, T8 | done |
| T10 | List and end my Sign-in Sessions and answer "who am I", Owner-scoped end to end | ports | Anton Husiev | M | T4, T8 | done |
| T11 | Wire Spring Security WebAuthn for passkey registration and passkey sign-in into the one session mechanism | wiring | Anton Husiev | M | T8 | done |
| T12 | List and remove my Passkeys, filtered by my WebAuthn user entity | ports | Anton Husiev | S | T11 | done |
| T13 | Set up the SPA router, query client and fetch client with failure routing, and build the system pages SCR-91, SCR-92 and SCR-93 | ui | Anton Husiev | M | — | done |
| T14 | Build Sign in (SCR-01, email part) and Check your email (SCR-07) with the Sign-in Code | ui | Anton Husiev | M | T13 | done |
| T15 | Build Confirm sign-in link (SCR-08) and the landing-after-sign-in rule | ui | Anton Husiev | S | T14 | done |
| T16 | Build Create a passkey (SCR-09) and "Sign in with a passkey" on SCR-01 | ui | Anton Husiev | M | T15 | done |
| T17 | Build the signed-in PageFrame with Sign out and the empty Inbox (SCR-10) | ui | Anton Husiev | S | T13 | done |
| T18 | Build Profile and security (SCR-64): passkeys card and sign-in sessions card | ui | Anton Husiev | M | T16, T17 | done |
| T19 | Make teleX start with one command: Dockerfile, compose with app and Mailpit, README and a smoke script | docs | Anton Husiev | M | T9, T14 | done |
| T20 | Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator | tests | Anton Husiev | M | T7, T10, T12, T15, T16, T18, T19 | done |
| T21 | Reject addresses the mail library can't parse and roll back the grant on any send failure | app | Anton Husiev | S | — | done |
| T22 | Serialise Sign-in Grant issue per canonical address so at most one grant is live | app | Anton Husiev | S | T21 | done |
| T23 | Harden passkey registration: long emails, concurrent user-entity insert, honest 5xx, one transaction | wiring | Anton Husiev | S | — | done |
| T24 | Make framework errors use contract problem codes and validate API bodies against openapi.yaml | ports | Anton Husiev | S | T22 | done |
| T25 | Prove passkey sign-in end to end: removed passkey refused, success ends the held session and sends the notice | tests | Anton Husiev | S | T23 | done |
| T26 | Prove session lifetime over HTTP and that raw secrets never reach logs or the event registry | tests | Anton Husiev | S | T24 | done |
| T27 | Keep page actions correct across the Unavailable page: Retry carries its result, SCR-08 shows unusable, bare layout | ui | Anton Husiev | S | — | done |
| T28 | Mark /me refetches as background and cover the untested SCR-64 and SCR-07 states | ui | Anton Husiev | S | T27 | done |
| T29 | Bring the auth and signed-in pages in line with screens.md and the accessibility rules | ui | Anton Husiev | S | T28 | done |
| T30 | Make the e2e run reliable: app healthcheck, pinned and type-checked e2e, strict AC-82 assertion, removed-passkey e2e | tests | Anton Husiev | S | T25 | done |
| T31 | Keep the saved Retry across background failures and count page opens as activity | ui | Anton Husiev | S | — | todo |
| T32 | Keep focus inside ConfirmDialog while busy and land it on a stable element after removal; ship a right-sized logo | ui | Anton Husiev | S | T31 | todo |
| T33 | Prove the 44 px PageFrame touch targets at phone width in the browser | tests | Anton Husiev | S | T32 | todo |
| T34 | Use contract error codes for passkey registration failures and validate the WebAuthn calls against openapi.yaml | wiring | Anton Husiev | S | — | done |
| T35 | Accept only a plain mailbox as an email address | app | Anton Husiev | S | T34 | done |
| T36 | Make the rollback check see column length and the failed-notice assertion exact | tests | Anton Husiev | S | T35 | todo |

**Total:** 36 tasks (T21–T36 are review follow-ups from `_review/review-2026-10-02.md`), ~25 person-days (S = ½ day, M = 1 day).
