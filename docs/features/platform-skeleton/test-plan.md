---
status: Draft
owner: "Anton Husiev (QA)"
reviewers: ["Anton Husiev (implementing engineer)", "Tech Lead"]
updated_at: "2026-10-02"
feature_size: "M"
---

# Test plan — platform-skeleton

teleX's front door. One command starts a working installation. An Owner signs up and signs in with an emailed Sign-in Link (confirmed on its page) or its Sign-in Code, and can add an optional Passkey. Owners see and end their own Sign-in Sessions, get a "New sign-in to teleX" email for every later sign-in, and always land on a clear system page instead of a dead end. Coverage below maps every `spec.md` §5 AC (24) to named tests. The e2e-through-UI paths follow `ux-flows.md`, and the component states follow `screens.md`.

## Levels

`sad.md` declares `target_surfaces: [backend-service, web-frontend]`, so the UI tiers apply.

| Level | Scope | Strategy (generic — no tool names) |
|---|---|---|
| Unit | Pure rules with no I/O: email canonicalisation and validation, the Sign-in Grant state rules (15 min, single use, 5 wrong codes, superseded), the 30/90-day session rules, device naming, the safe return-path rule, notice time formatting. | In-memory, with a fixed clock passed in. |
| Integration | `identity`, `mail` and `web` against the real Postgres they own, plus a real mail server. Includes the Spring Security chain (cookie, CSRF, no-store), WebAuthn ceremonies at the API level, and the after-commit notice listener. | Throwaway Postgres container (the repo's pgvector image) and a throwaway local mail server container for each suite. A controlled clock is injected for every time rule. |
| Contract | Real API responses, including `application/problem+json` errors, checked against `contracts/openapi.yaml`. The `SignInSessionStarted` payload is checked against `contracts/events.md`. | Validate the real response and event bodies produced in integration tests against the agreed documents. No hand-written stubs. |
| E2E | AC-33 only: the one command on a clean checkout, scripted without a browser. | Start the full compose stack, wait for health, check that the README addresses answer and that a sign-in email reaches the local mailbox, then tear the stack down. |
| Load | — | <!-- N/A: no numeric throughput or latency NFR (spec §6 Availability is N/A) --> |
| Component | Each screen in `screens.md` (SCR-01, 07, 08, 09, 10, 64, 91, 92, 93) in every state its manifest row lists, plus the fetch client's failure routing and `PageFrame`. | Render in isolation with the API answered by in-test handlers; assert copy from the message catalog, focus, and the busy, disabled and alert states. |
| Visual-regression | — | <!-- N/A: no visual-diff baseline in E01. Layout is covered by e2e-through-UI at 360 px and 1280 px plus the accessibility scan. Revisit when E06 brings AppShell. --> |
| E2E-through-UI | The `ux-flows.md` flows in a real browser against the full app, a throwaway Postgres and the local mail server. | Every test runs at 360 px and at 1280 px, with an accessibility scan on each screen it visits (0 violations). Passkeys use a virtual authenticator. Emails are read from the local mailbox's API. Time-based paths use a clock that exists only in the test profile and can be moved forward. |

## AC coverage

| AC (spec.md §5) | Test name (intent-based) | Level | Expected outcome |
|---|---|---|---|
| AC-33 | one command brings up the sign-in page and the local mailbox | e2e | On a clean checkout, the README command starts the stack; the README's app address serves the sign-in page and the mailbox page answers |
| AC-33 | first sign-in completes with the email from the local mailbox | e2e | A sign-in email requested on the fresh stack appears in the local mailbox, and its link signs the first Owner in |
| AC-33 | sign-in email is delivered over SMTP to the local mailbox | integration | The mail adapter delivers to the throwaway mail server with the expected recipient, subject and body |
| AC-34 | addresses that differ only by letter case or a +tag canonicalise to one address | unit | `Anton+work@Mail.com` and `anton@mail.com` give the same canonical address |
| AC-34 | first redeem creates the Owner and signs in that browser | integration | One Owner exists, a Sign-in Session is started for the browser that confirmed, and the answer says the account was created |
| AC-34 | signing in again with a variant of the address opens the same account | integration | No second Owner; the session belongs to the original Owner |
| AC-34 | sign-in email goes to the address as typed, notice to the address the account was created with | integration | The sign-in email's recipient is the typed variant; a later "New sign-in to teleX" goes to the creation address |
| AC-34 | new person signs up by link and reaches the empty Inbox | e2e-through-UI | SCR-01 → SCR-07 → link from the mailbox → SCR-08 "Continue as <address>" → SCR-09 passkey offer → SCR-10 with the single step "Connect Telegram" |
| AC-82 | correct code redeems the grant and voids its link | unit | The grant is used; a later link redeem is refused as already used |
| AC-82 | code typed on the asking browser starts the session there | integration | The browser that typed the code holds the new session; the link from the same email is refused afterwards |
| AC-82 | code from the email signs in the laptop while the link then fails | e2e-through-UI | Two browser contexts: the laptop types the code and lands signed in; opening the link in the "phone" context shows "This link was already used" |
| AC-35 | grant is expired at 15 minutes, checked at confirm and at code | unit | At 14:59 it is usable; at 15:00 or later, link confirm and code are refused as expired |
| AC-35 | link page opened in time but confirmed after 15 minutes is refused | integration | Reading the link at 14 min succeeds; confirming at 16 min is refused as expired, and no session starts |
| AC-35 | expired code is refused | integration | Typing a correct code after 15 minutes is refused as expired |
| AC-35 | "Send a new link" emails a fresh link to the same address | integration | A new grant is issued for the expired grant's address and a new email arrives |
| AC-35 | expired state on the code and link pages | component | SCR-07 and SCR-08 show the expired message with a "Send a new link" action |
| AC-35 | expired link shows the expiry and a fresh link works | e2e-through-UI | After the test clock moves 16 min, confirming shows expired; "Send a new link" delivers a new email whose link signs in |
| AC-83 | address validator refuses incomplete addresses | unit | Missing name, missing @, domain without a dot and `me@localhost` are refused; `me@example.com` is accepted |
| AC-83 | invalid address sends no email | integration | Refused as a validation failure on the field; the mail server receives nothing and no grant is stored |
| AC-83 | sign-in field explains a complete address | component | SCR-01 shows the field error "enter a complete email address" text from the catalog, linked to the input for screen readers |
| AC-84 | a used grant refuses both link and code | unit | After one redeem, any further link or code redeem is refused as already used |
| AC-84 | reusing a link or code is refused | integration | The second redeem is refused as already used; no new session starts |
| AC-84 | already-used state offers a new link | component | SCR-08 and SCR-07 show "This link was already used" with "Send a new link" |
| AC-84 | opening a used link in the browser shows "already used" | e2e-through-UI | Opening the same link again shows the already-used message and "Send a new link" |
| AC-85 | the 5th wrong code voids the grant | unit | Wrong attempts 1–4 leave it usable; the 5th voids it; a 6th code, even the correct one, is refused |
| AC-85 | wrong attempts are counted in storage and a voided email's link is refused | integration | The count survives across requests; after 5 wrong codes, the link from that email is refused as no longer valid |
| AC-85 | voided state asks for a new email | component | SCR-07 tells the person the code is no longer valid and offers a new email |
| AC-85 | five wrong codes void the email in the browser | e2e-through-UI | After 5 wrong codes, SCR-07 shows the void message; the link from that email is refused too |
| AC-86 | reading a link never redeems it | unit | Previewing leaves the grant unused |
| AC-86 | link opened by a scanner and confirmed later signs in | integration | A preview read with no confirm, then a confirm within 15 minutes, starts the session normally |
| AC-86 | person confirms a link that a preview opened first | e2e-through-UI | The test fetches the link once without confirming, then the person opens it and confirms, and lands signed in |
| AC-103 | a newer grant supersedes earlier ones for the canonical address | unit | The earlier grant is refused as expired once a newer one exists |
| AC-103 | only the newest email for an address works | integration | Link and code from the earlier email are refused as expired, including when it was sent to a +tag variant |
| AC-103 | a code counts only against the page's own grant | integration | A code typed on SCR-07 for grant A never redeems or counts a wrong attempt on grant B |
| AC-103 | superseded state offers a new link | component | SCR-07 and SCR-08 show the expired message with "Send a new link" |
| AC-103 | link from an earlier email is refused in the browser | e2e-through-UI | After two requests, the first email's link shows expired; the second email's link signs in |
| AC-104 | redeeming in a signed-in browser replaces its session | integration | The browser's held session is ended, a new one starts for the Owner the email was sent to, and the notice per AC-98 is sent |
| AC-104 | redeeming another Owner's email in a signed-in browser switches the Owner | integration | Owner A's session in that browser is ended; the browser is now Owner B's, and A's other sessions are untouched |
| AC-89 | device name is generated from browser and device | unit | Known user agents give names like "Safari on iPhone" and "Chrome on Mac"; unknown ones get a generic fallback |
| AC-89 | registered passkey is listed with its name and dates | integration | After registration the list shows the generated name, the creation date and "Never used"; after a passkey sign-in the last-used date is set |
| AC-89 | passkey sign-in starts the one kind of session | integration | A passkey assertion starts a Sign-in Session with the same cookie and rules as email sign-in |
| AC-89 | passkey row shows name, created and last used | component | SCR-64's passkeys card renders each row, including "Never used" |
| AC-89 | create a passkey after sign-up and sign back in with it | e2e-through-UI | With the virtual authenticator: create on SCR-09, sign out, then "Sign in with a passkey" on SCR-01 signs in without typing an email |
| AC-90 | passkey step explains an unsupported browser | component | With no passkey support detected, SCR-09 explains it and offers to add one later from another device, and continues to SCR-10 |
| AC-105 | cancelled or failed passkey creation stays on the step | component | SCR-09 shows that no passkey was created, with "Try again" and "Not now" |
| AC-105 | failed registration adds no credential | integration | A registration that never completes or is rejected leaves the Owner's passkey list unchanged |
| AC-91 | "Not now" lands on the Inbox | component | SCR-09 "Not now" navigates to SCR-10 |
| AC-91 | empty passkey list offers to add one | component | SCR-64 shows "No passkeys yet" with "Add a passkey" |
| AC-91 | passkey step follows only the account-creating sign-in | integration | The redeem answer says the account was created only on the first sign-in, never on later ones |
| AC-91 | "Not now", then a later sign-in skips the step | e2e-through-UI | Sign-up → "Not now" → SCR-10; sign out and sign in again → straight to SCR-10, with no SCR-09 |
| AC-92 | removing the only passkey keeps email sign-in and open sessions | integration | The passkey is gone from the list and its assertion is refused; email sign-in works; the Owner's sessions on every device stay live |
| AC-92 | remove confirmation reminds about lost-device sessions | component | SCR-64's confirm step names ending the lost device's session in the sessions list |
| AC-93 | sessions list shows own sessions with the current one marked | integration | Each entry carries browser, device type and last activity; exactly one is flagged as the caller's |
| AC-93 | ending another session makes its next request "session ended" | integration | After the end, that session's next request is refused as session ended |
| AC-93 | sessions card renders entries and "This device" | component | SCR-64's sessions card shows each row's details and the "This device" marker, and offers no end action on it |
| AC-93 | end the phone's session from the laptop | e2e-through-UI | Two browser contexts: the laptop ends the phone's session, and the phone's next action lands on SCR-92 "Session ended" |
| AC-94 | sign out of all other sessions keeps only the current one | integration | Every other session of the Owner is ended; the current one stays; other Owners' sessions are untouched |
| AC-94 | "Sign out of all other sessions" leaves only this device | e2e-through-UI | With three contexts signed in, the list then shows only "This device", and the other contexts land on SCR-92 |
| AC-95 | sign out ends the session and nothing is cacheable | integration | The session is ended, the cookie is cleared, and responses with Owner data are marked not to be stored |
| AC-95 | sign out, then going back shows no data | e2e-through-UI | "Sign out" lands on SCR-01; browser Back shows no Owner data (it lands on sign-in) |
| AC-96 | session lifetime rules at their boundaries | unit | Idle 30 days ends it, while 29 days 23 h does not; 90 days after start ends it even when active |
| AC-96 | background refreshes don't count as activity | integration | Requests marked as background don't move last activity; unmarked requests move it at most once a minute |
| AC-96 | expired session is refused on the next request | integration | With the clock past 30 days idle or 90 days total, the request is refused as session ended |
| AC-96 | session-ended answer routes to the "Session ended" page | component | The fetch client sends a session-ended refusal to SCR-92, whose "Sign in again" opens SCR-01 |
| AC-97 | an Owner sees only their own sessions and passkeys | integration | Each Owner's lists contain only their own records |
| AC-97 | ending another Owner's session looks like it doesn't exist | integration | Refused as not found; that session stays live |
| AC-97 | removing another Owner's passkey looks like it doesn't exist | integration | Refused as not found; that passkey still works |
| AC-98 | notice time shows the browser's zone with its name, and UTC | unit | A fixed instant and zone `Europe/Kyiv` format to the local time with the zone name plus the UTC time |
| AC-98 | notice is sent for every non-creating sign-in | integration | Link, code and passkey sign-ins of an existing Owner each send one notice after commit, naming browser, device type and time, with a link to the sessions list |
| AC-98 | no notice for the account-creating sign-in | integration | The first sign-in's event is completed without an email |
| AC-98 | second sign-in delivers "New sign-in to teleX" | e2e-through-UI | After a sign-out and a new sign-in, the local mailbox holds the notice, and its link opens SCR-64's sessions list (via SCR-01 when signed out) |
| AC-100 | empty Inbox shows only "Connect Telegram" | component | SCR-10 shows the single step; its action opens the "Telegram linking is coming next" note |
| AC-100 | Owner without a Linked Account is reported as such | integration | "Who am I" reports no Linked Account for a new Owner |
| AC-100 | new Owner lands on the empty Inbox | e2e-through-UI | Covered in the AC-34 journey: SCR-10 shows "Connect Telegram" and the note opens |
| AC-101 | only teleX paths are accepted as a return destination | unit | Same-origin relative paths are kept; absolute, protocol-relative, other-host and backslash forms fall back to the Inbox |
| AC-101 | unauthenticated answer sends to sign-in with the path remembered | component | The fetch client routes an unauthenticated refusal to SCR-01 and keeps the current path |
| AC-101 | deep link → sign in → original page | e2e-through-UI | Signed out, opening SCR-64's address leads to SCR-01; after signing in, the Owner lands on SCR-64; with a foreign destination they land on SCR-10 |
| AC-102 | unknown API path is not found, unknown page serves the app | integration | An unknown `/api/**` path and a missing asset are not found; an unknown client route gets the SPA |
| AC-102 | timeouts and server failures route to "teleX is unavailable" | component | No answer in 10 s, any server failure, or a forbidden (CSRF) answer shows SCR-93; "Retry" repeats the same request |
| AC-102 | "Page not found" and "teleX is unavailable" in the browser | e2e-through-UI | An unknown address shows SCR-91 and "Go to Inbox" opens SCR-10; with the backend made to fail for an action, SCR-93 appears and "Retry" repeats it once it recovers |
| AC-34, AC-82, AC-83, AC-84, AC-85, AC-93, AC-97, AC-101 | every response matches the API contract | contract | Success and problem bodies (type, code, errors[]) match `contracts/openapi.yaml` for each endpoint the integration tests call |
| AC-98 | session-started event matches its schema | contract | `SignInSessionStarted` carries `ownerId`, `sessionId` and `createdAccount` per `contracts/events.md` |

## Edge cases / error paths

Error and authorization ACs (AC-35, 83, 84, 85, 90, 97, 101, 102, 105) each have their own rows above. Further cases that the spec and SAD imply:

- Two concurrent redeems of the same link or code → exactly one session starts; the other is refused as already used (integration).
- Two concurrent first sign-ins for one canonical address → exactly one Owner is created (integration).
- Mail server unavailable or too slow when a sign-in email is requested → server failure, no grant kept, and no earlier grant superseded (sad §6 Flow US-01). The UI shows SCR-93 with Retry (integration + component).
- Mail server unavailable for the "New sign-in" notice → the publication stays incomplete and is resubmitted on restart; the sign-in itself is unaffected (integration).
- Wrong code typed for a grant that is already expired, used or superseded → refused with that state's reason, and the wrong-attempt count doesn't move (unit).
- SCR-07 opened with no grant (reload or direct visit) → redirects to SCR-01 (component; screens.md Noted gap 1 default).
- A request that changes state but has a missing or wrong CSRF token → refused, nothing changes, and the SPA shows SCR-93 (integration + component; screens.md Noted gap 2 default).
- A link token, Sign-in Code or session key is never stored or logged in readable form → only hashes appear in `sign_in_grant` and `sign_in_session`, and captured app logs never contain the plaintext values (integration).
- Session cookie is HttpOnly, SameSite and Secure behind HTTPS (integration).
- Passkey assertion with a credential that has been removed → refused; the SCR-01 passkey attempt shows "no usable passkey" and email sign-in stays available (integration + component).
- Sign out fails on the server → SCR-93, and the session is still live (component).
- Return path pointing at a system page or SCR-01 itself → lands on SCR-10 (unit).

## Test data

- **Seed strategy:** the `data-model.md` §Test fixtures builders — `anOwner(...)`, `aGrant(...)` (returns the plaintext link token and code), `aSession(...)` (returns the plaintext cookie value), and passkeys saved through the WebAuthn credential repository with label `Test Browser on Test Device`. Addresses use `example.test` with a random local part per test. Every time value comes from the test clock. There are no bootstrap seeds: the first Owner is created by signing in.
- **Integration dependency:** a throwaway Postgres container (the repo's pgvector image, migrated by Flyway on start) and a throwaway local mail server container. One of each per suite. No mocked datastore or mailer at this level.
- **Cleanup boundary:**
  - Integration: per test, by isolation — each test makes its own Owner with a unique address and asserts only on that Owner's rows. Tests that check counts across all rows (one Owner per canonical address, the mail server's inbox) clear those tables and the mailbox first. The containers are torn down per suite.
  - E2E-through-UI: per test. Each test signs up a fresh unique address in its own browser context(s), so tests never share Owners. The stack is reset per run.
  - E2E (AC-33): the compose stack and its volumes are removed after the run.
- **Clock:** unit and integration tests inject a fixed clock. E2E-through-UI moves a test-profile clock that the `local` and production profiles don't expose.

## NFR validation (load)

<!-- N/A: no numeric NFR to load-test. spec §6 has no throughput or latency target, and Availability is N/A. -->

The other numeric §6 NFRs are covered at other levels:

- 15-minute single-use link, and a 6-digit code with ≤ 5 wrong attempts → unit + integration with a controlled clock (AC-35, AC-84, AC-85).
- 30 days idle / 90 days total → unit + integration with a controlled clock (AC-96).
- 360 px and 1280 px, WCAG 2.2 AA → every e2e-through-UI test runs at both widths with an accessibility scan (0 violations).
- Passkeys in Chrome, Safari (macOS) and Safari (iOS) → e2e-through-UI with a virtual authenticator in Chromium, plus a **manual** check on Safari macOS and iOS recorded in the E01 pull request.
- ≤ 5 min from install to sign-in page → **manual** timed run on a clean machine, recorded in the E01 pull request. The scripted AC-33 e2e checks that the stack works, not the timing.
- A deliberate detekt/ktlint violation fails CI → **manual** one-off check recorded in the E01 pull request.

## CI placement

- **On every PR:** unit, component, integration (Docker is already in CI), contract, and the existing `ModularityTest` and `MigrationRollbackIT`.
- **On every PR, as a separate job:** e2e-through-UI at both widths with the accessibility scan. This is the E01 DoD gate. Move it to pre-merge on master only if it slows PRs too much.
- **Pre-release (before the E01 PR is merged):** the AC-33 one-command e2e on a clean runner, plus the three manual checks above.
