---
status: Draft
owner: "Anton Husiev (QA)"
reviewers: ["Anton Husiev (implementing engineer)", "Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
---

# Test plan — telegram-link

An Owner links their own Telegram account from the browser. The wizard takes the phone number, then the code, then the password if the account has two-step verification. teleX keeps the session encrypted with a per-Owner key, syncs the chat list with visible progress and reconnects by itself after a restart. A lost session is not an unlink: the account waits for "Sign in again". Unlinking is explicit and total: no session, no stored data and no teleX device left in Telegram, and the unlink is announced so that it survives a restart.

## Levels

`sad.md` declares `target_surfaces: [backend-service, web-frontend]`, so the UI tiers apply. Telegram is always the in-memory **fake Telegram adapter** (`telex.telegram.adapter=fake`, scripted by test phone numbers per T4), except the real-Telegram rows marked **manual** below.

| Level | Scope | Strategy (generic — no tool names) |
|---|---|---|
| Unit | Pure rules with no I/O: the masked phone, the Linked Account rules and states (one Owner per Telegram account, duplicate vs Sign in again, the account limit, the counting of a Session lost account), the linking attempt's state machine (one per Owner, 15-minute expiry, the allowed step order), the mapping of Telegram's refusals and waits to attempt outcomes, and the rule that only a confirmed session end means Session lost. | In-memory, with a fixed clock. |
| Integration | `messaging`, `identity`, `telegram` and `web` against the real Postgres they own, with the fake Telegram adapter and a throwaway session directory. Covers the REST endpoints, the live-update stream, the event publication registry, boot-time reconnect and restarts of the app context. | A throwaway Postgres container (the repo's pgvector image, migrated by Flyway on start) per suite, plus a temporary directory as the session volume. Restart tests stop and start the app context against the same container and directory. No mocked datastore. |
| Contract | Real API responses, including problem bodies, checked against `contracts/openapi.yaml`. The four durable `messaging.*` events and the live-update hint checked against `contracts/events.md`. | Validate the real response, event and stream bodies captured in the integration tests against the contract files. No hand-written stubs. |
| E2E | — | <!-- N/A: there is no API-only full flow worth more than its integration tests. The critical user stories run end to end through the UI (e2e-through-UI). --> |
| Load | NFR-04 capacity (50 connected accounts) and the wizard step p95. | The load tool already in your repo, or e.g. k6 or Locust; the capacity run is a one-off on Telegram's test servers (see NFR validation). |
| Component | SCR-02, SCR-10 and SCR-60 in every state that `screens.md` lists, plus the new and changed components: `LinkedAccountSummary`, `StatusBanner`, `CodeInput`, the unlink dialog, the shell's `StatusBanner` in `AppShell` and `OnboardingLayout` (the C-04 `account-disconnected` condition), and the single live-update client. | Render each piece in isolation, with the API and the live-update stream answered by in-test handlers. Assert the copy from the message catalog, focus and `aria-live`, and that state never shows by color alone. |
| Visual-regression | — | <!-- N/A: no visual-diff baseline (as in E01). Layout is covered by e2e-through-UI at 360 px and 1280 px plus the accessibility scan. Revisit when E06 brings AppShell. --> |
| E2E-through-UI | The `ux-flows.md` flows for US-02, US-03 and US-51 in a real browser against the full app on the fake adapter, a throwaway Postgres and the local mail server (for sign-up). | Every test runs at 360 px and at 1280 px, with an accessibility scan on each state it visits (0 violations). The fake adapter's terminate, outage and wait behaviour is triggered by the test phone numbers. |

## AC coverage

| AC (spec.md §5) | Test name (intent-based) | Level | Expected outcome |
|---|---|---|---|
| AC-01 | phone is masked to the country code and the last two digits | unit | A test number renders as `+999 ••• ••00`; the middle is fixed decoration, whatever the real length |
| AC-01 | completing phone, code and password links a new account | integration | One Linked Account for the Owner in the Connected state, with the Telegram name and masked phone; the session key is stored only sealed; `AccountLinked` is published; the Owner's "who am I" reports one Linked Account |
| AC-01 | an account without two-step verification links after the code | integration | The code step answers "linked" directly, with no password step |
| AC-01 | chat sync starts as soon as the account is linked | integration | Right after linking, the account reports a chat total and a synced count that rises to the total |
| AC-01 | wizard steps move phone → code → password | component | SCR-02 shows each step with its copy; the password step appears only when the step result asks for it; success returns to the origin with the "connected" Toast |
| AC-01 | Inbox swaps "Connect Telegram" for the account line | component | SCR-10 with no account shows the single step; with one it shows the account line with its state and sync progress, linking to SCR-60 |
| AC-01 | new Owner links an account with two-step verification | e2e-through-UI | Sign up → SCR-10 "Connect Telegram" → phone → code → password → SCR-10 shows the account line "Connected, syncing" and no "Connect Telegram" step |
| AC-02 | Telegram's wait is mapped to an ended attempt with a retry time | unit | A wait answer on the code or phone step ends the attempt and carries the instant when the Owner can try again |
| AC-02 | wrong or expired code can be retried or replaced | integration | A wrong code and an expired code are each refused with their own reason, the attempt stays at the code step, and "send a new code" issues a fresh code that then links |
| AC-02 | attempts limited by Telegram end the attempt with a wait | integration | The attempt is discarded, the answer says when the Owner can try again, and the fake holds no session from it |
| AC-02 | starting again with the same number during the wait sends no code | integration | The phone step answers with the remaining wait; the fake records no new code sent |
| AC-02 | code error and wait countdown | component | SCR-02 shows "That code is not right" with retry and "Send a new code"; the "Too many attempts" state counts down to the time and then offers "Start again" |
| AC-02 | the wait card shows the Owner's time and is free of device-clock skew | component | With the Owner's saved zone different from the device's, the card shows `retryAt` as HH:mm in the saved zone; with the device clock 10 minutes off, it counts down the `Retry-After` seconds from when the answer arrived and offers "Start again" only after them. When the Owner's zone arrives after the card, the `role="status"` sentence also uses the saved zone. The API client carries `Retry-After` on the failure, and drops a value that is not whole seconds (an HTTP-date, `abc`, `-5`, `1.5`), so the countdown falls back to `retryAt` (unit). Vitest runs with `TZ=UTC` (`vite.config.ts`) so the saved zone always differs from the device's |
| AC-02 | wrong code, retry, then wait countdown in the browser | e2e-through-UI | A wrong code shows the error and the correct one continues; with the wait test number the wizard ends with the countdown, and starting again with that number shows the remaining wait |
| AC-106 | wrong password is refused with the Owner's hint | integration | The attempt stays at the password step; the answer carries the hint the fake account has, or none |
| AC-106 | password error state | component | SCR-02 clears and focuses the field, shows "That password is not right", the hint when set, and that a forgotten password can only be reset in the Telegram app |
| AC-107 | invalid, unknown and banned numbers are refused with their reason | integration | Each of the three test numbers is refused at the phone step with its own reason; the attempt stays at the phone step; teleX never asks Telegram to create an account |
| AC-107 | phone step says which refusal it is | component | SCR-02 shows the matching field error for each reason; the unknown-number one says to create the account in the Telegram app first |
| AC-04 | one Telegram account belongs to one Owner | unit | Completing for a Telegram identity owned by another Owner gives the "belongs to another Owner" outcome, never a new account |
| AC-04 | linking another Owner's Telegram account is refused and signed out | integration | Owner B's attempt is refused with the one-Owner rule; the fake shows the session B just made as logged out; Owner A's Linked Account and chats are unchanged |
| AC-04 | two Owners completing for the same Telegram account at once | integration | Exactly one Linked Account exists; the other completion is refused and its session logged out |
| AC-04 | "linked elsewhere" refusal | component | SCR-02 shows "This account is linked elsewhere" with the one-Owner rule and a way back to the origin |
| AC-108 | duplicate vs Sign in again is decided by Telegram identity | unit | Same identity + Connected → "already linked"; same identity + Session lost → "sign in again" to the same account; a changed phone with the same identity still matches |
| AC-108 | relinking a connected account is refused and the extra sign-in ended | integration | Still one Linked Account; the answer says already linked; the fake shows the new session logged out and the original session intact |
| AC-108 | relinking a Session lost account brings back the same account | integration | The same Linked Account id is Connected again on the new session, with the name and masked phone Telegram reports now; its chat rows are kept |
| AC-108 | "already linked" refusal | component | SCR-02 shows "Already linked" and returns to the origin |
| AC-109 | attempt expires 15 minutes after the last step | unit | At 14:59 since the last step it is still open; at 15:00 it is discarded; each step resets the timer |
| AC-109 | one open attempt per Owner, resumed at its step | integration | Starting again while an attempt is open returns the same attempt at its current step, also from another Sign-in Session of the same Owner |
| AC-109 | cancel and inactivity leave no Telegram session | integration | After a cancel, and after the clock passes 15 minutes, the attempt is gone, the fake holds no session from it, the session directory is removed, and the next start begins at the phone step |
| AC-109 | ended attempt state | component | When the attempt is not found on load or on a step, SCR-02 shows "This linking attempt ended" with "Start again" |
| AC-109 | reload and a second tab continue the wizard; cancel starts over | e2e-through-UI | At the code step, a reload and a second tab both show the code step; Cancel returns to the origin, and "Connect Telegram" then opens the phone step |
| AC-110 | signing out of teleX leaves Linked Accounts connected | integration | After sign-out, and after the Owner's session is ended or revoked, the account is still Connected and syncing; the next sign-in lists it |
| AC-110 | a Sign-in Session ending mid-wizard discards the attempt | integration | The attempt of that session is discarded, the fake holds no session from it, and after a new sign-in the wizard starts at the phone step |
| AC-03 | another Owner's account and chats behave as missing | integration | For each of list, Sign in again, unlink and the live-update stream, Owner A's request about B's account answers as not found, or the stream sends no hint about it; B's account and chats are unchanged |
| AC-03 | Operator-visible output holds no Owner's Telegram data | integration | After link, sync, wizard errors and unlink, the captured logs, the metric tags and the stored event payloads contain none of the fake account's phone digits, name, Telegram ids, chat titles, code or password |
| AC-03 | not-found answers match the contract | contract | Cross-Owner requests return the same not-found problem body as a truly unknown id |
| AC-111 | unlink signs out and deletes everything stored for the account | integration | The fake shows the session logged out; no `linked_account` or `channel` row and no sealed key remain; the session directory is deleted; `AccountUnlinked` is published; with no account left, "who am I" reports none |
| AC-111 | the session directory is removed even if the app stops mid-unlink | integration | The unlink commits, the app stops before the directory is deleted, and on restart the orphan sweep deletes it |
| AC-111 | unlink dialog names what will happen | component | The dialog names the account and the consequences, with the synced chat count in correct English ("the 1 chat", no count clause at 0); confirming removes the row; on the last account the origin becomes SCR-10 with "Connect Telegram" |
| AC-111 | unlink the only account | e2e-through-UI | Link an account → SCR-60 shows it Connected → Unlink → confirm → the info Toast "`<name>` is unlinked." and no "couldn't confirm" text → SCR-10 shows "Connect Telegram" again. The Session lost unlink in the AC-117 test shows the AC-113 error Toast instead |
| AC-112 | the unlink announcement is delivered after a restart | integration | A test listener for `AccountUnlinked` gets nothing before the stop; after the restart it receives the event once from the publication registry |
| AC-112 | an unlinked account is gone everywhere, also after restart | integration | After the unlink, and after a restart, the account is in no list, count or live hint, and it can't be picked as a target for Sign in again |
| AC-112 | relinking an unlinked Telegram account creates a new account | integration | Linking the same fake identity again creates a Linked Account with a new id and no chat rows from before |
| AC-112 | unlink event matches its schema | contract | `messaging.account-unlinked.v1` carries only teleX ids, as in `contracts/events.md`, and no Telegram data |
| AC-113 | unlink with Telegram unreachable or the session lost still deletes everything | integration | With the fake unreachable, and separately with the account Session lost, the rows, key and directory are gone, the answer says the sign-out wasn't confirmed, and nothing is kept for a retry |
| AC-113 | unlink not confirmed by Telegram | component | After the unlink, SCR-60 shows that Telegram couldn't confirm the sign-out and that the Owner should check active sessions in the Telegram app |
| AC-114 | a second account links and syncs on its own | integration | With a limit above one, two Linked Accounts are listed, each with its name, masked phone and state, and each has its own chat rows and counts |
| AC-114 | Accounts page lists several accounts | component | SCR-60 renders each account's row with its name, masked phone and state badge, and "Add account" opens SCR-02 with the accounts origin |
| AC-115 | limit counts every account, Session lost included | unit | At the limit a new link is refused; a Session lost account counts; Sign in again to it takes no new place |
| AC-115 | "Add account" at the limit doesn't start the wizard | integration | Starting an attempt at the limit is refused with the limit, and no attempt opens |
| AC-115 | the last place taken during the wizard ends the sign-in | integration | Two attempts are open below the limit; the second to complete is refused with the limit, and the fake shows its session logged out |
| AC-115 | lowering the limit keeps existing accounts | integration | With the configured limit below the current count, every account stays and keeps working; only new links are refused |
| AC-115 | limit refusals in place | component | SCR-60 "Add account" and the end of the SCR-02 wizard show "Account limit reached" with the limit and the hint to unlink one |
| AC-116 | sync progress counts every chat, archived included | integration | A 500-chat fake account (50 archived) reports a total of 500, a rising synced count, and the chat count once finished |
| AC-116 | sync continues after a restart mid-sync | integration | The app stops after a part of the chats are synced; after the restart the sync goes on to 500 without duplicate rows and is marked finished |
| AC-116 | sync progress on the account line | component | `LinkedAccountSummary` shows "N of total" while syncing and the chat count when done; progress ticks aren't announced, state changes are |
| AC-121 | chat changes in Telegram reach the account within a minute | integration | When chats are joined, left or renamed, or new messages arrive in the fake, the chat rows and the count reflect it, and a live hint is sent to the Owner's stream within 60 s |
| AC-121 | a live hint refreshes the account in place | component | On a `linked-accounts` hint the shown count updates through a background refetch, with no skeleton and no Toast |
| AC-117 | only a confirmed session end means Session lost | unit | A closed authorization moves Connected → Session lost; a connection loss moves it to Reconnecting only |
| AC-117 | a session ended in Telegram shows Session lost within 5 minutes | integration | After the fake terminates the session, the account is Session lost within the window on a controlled clock; it keeps its chat rows |
| AC-117 | Sign in again with the same Telegram account restores the same account | integration | The same Linked Account id is Connected again, with its chat rows kept and the session key resealed for the new session |
| AC-117 | Sign in again with a different Telegram account is refused | integration | It is refused as a different account and the fake shows that sign-in logged out; if the other identity belongs to another Owner, the one-Owner rule is answered instead |
| AC-117 | "a different account" refusal | component | SCR-02 for a target account shows "A different account" and tells the Owner to link it as a new account |
| AC-117 | session lost, then sign in again, in the browser | e2e-through-UI | The terminate test number links; after the fake ends the session, SCR-60 shows "Session lost" with "Sign in again"; signing in again shows the same account Connected |
| AC-122 | an outage shows Reconnecting and recovers by itself | integration | When the fake goes unreachable the account is Reconnecting and no banner condition holds; when it returns the account is Connected again with no Owner action |
| AC-122 | the banner condition holds only for Session lost | integration | "List my Linked Accounts" reports a Session lost account only after the fake confirms the end, never during an outage |
| AC-122 | Status Banner for one and several lost accounts | component | One lost account: the banner names it and "Sign in again" opens SCR-02 for it; several: it shows the count and "Open Accounts"; it has no close button and goes away when none is lost |
| AC-122 | Reconnecting badge | component | The SCR-60 row shows "Reconnecting" with an icon and text, with no action |
| AC-122 | Reconnecting and the disconnected banner in the browser | e2e-through-UI | An outage shows "Reconnecting" on SCR-60 and then "Connected" with no input; after a session end, the banner appears on SCR-10 and SCR-60 and goes away after "Sign in again" |
| AC-36 | two accounts reconnect after a restart without a code | integration | After stopping and starting the app context, both Linked Accounts are Connected within 60 s of readiness, and the fake received no code request |
| AC-118 | an account whose session ended while stopped shows Session lost after restart | integration | With two accounts, one terminated in the fake while the app is stopped: after the restart that one is Session lost and the other is Connected |
| AC-119 | linking without Telegram credentials isn't started | integration | With no API credentials, and with no master key before any Owner key exists, starting an attempt is refused as not set up; the app is still healthy and no attempt opens |
| AC-119 | "linking isn't set up" in place | component | SCR-10 "Connect Telegram" and SCR-60 "Add account" show the not-set-up message, and SCR-02 doesn't open |
| AC-120 | with credentials set, the wizard starts | integration | With API credentials and a master key configured, starting an attempt opens it at the phone step; the operator-visible output scan (AC-03 row) also covers this setup |
| AC-01, AC-02, AC-03, AC-04, AC-106, AC-107, AC-108, AC-109, AC-111, AC-113, AC-114, AC-115, AC-117, AC-119 | every response matches the API contract | contract | Success and problem bodies (type, code, errors[], and the retry time on waits) match `contracts/openapi.yaml` for each endpoint the integration tests call |
| AC-01, AC-116, AC-117, AC-121, AC-122 | durable events and live hints match their schemas | contract | `account-linked`, `linked-account-state-changed` and `linked-account-sync-progressed` events and the stream's hints carry exactly the fields in `contracts/events.md`, with no Telegram ids or titles |

## Edge cases / error paths

Every error and authorization AC (AC-02, 106, 107, 113, 117, 118, 119, 122 and AC-03) has its own rows above. Further cases that the spec, the SAD and the screens imply:

- A step that doesn't match the attempt's current step, for example a code sent while the attempt waits for a password → refused as a step mismatch, and the attempt doesn't move (integration + component; screens.md Noted gap 1).
- Telegram unreachable during a wizard step → that step fails with "Telegram is unavailable", the attempt stays at its step, and SCR-02 shows an error Toast (integration + component; screens.md Noted gap 2 default).
- Malformed step input: an empty phone, a code that isn't 5 digits, an empty password → refused as a validation failure on that field, and nothing is sent to Telegram (integration + component).
- The login code and the password are never stored, returned or logged → covered by the AC-03 scan, which includes them (integration).
- Session data at rest: the raw TDLib key captured in memory appears in no column of `linked_account`, `owner_key` or `event_publication`, in no log line, and in no byte of the session directory (integration; QG-1a).
- Master key missing or wrong once an Owner key exists → the app refuses to start; with no Owner key yet it starts and reports linking as not set up (integration; sad §7).
- Unlink of an account with an open Sign-in-again attempt → the attempt is discarded with the account, and its session is logged out (integration).
- Two tabs unlink the same account → one succeeds; the other answers as not found and the UI removes the row (integration + component).
- An unlink whose bounded sign-out times out → treated like unreachable (AC-113): everything is deleted and the "not confirmed" answer is given (integration).
- An unlink whose sealed key can't be opened (wrong, tampered or truncated) → the sign-out counts as not confirmed, and the account is still deleted (AC-113, AC-111; integration).
- An unlink whose delete fails after a confirmed sign-out → the session is closed and the account shows Session lost; a retried unlink deletes it without a sign-out (AC-122; integration).
- An unlink whose delete fails after an unconfirmed sign-out → the session stays open and keeps reporting its state, and Reconnecting returns to Connected (AC-122, AC-113; integration).
- A sign-out interrupted while TDLib finishes it → not confirmed, and a later Closed is still announced; closing the session on an interrupted thread disposes it without throwing (AC-122, AC-113; unit).
- An unlink whose sign-out is interrupted → the account is still deleted with the "not confirmed" answer, and the interrupt is handed back only after the delete, close and destroy, since a virtual request thread marked interrupted fails its JDBC calls. The fake records a close or destroy on an interrupted thread, and the test asserts neither happened (AC-113, AC-111; integration).
- An unlink whose sign-out fails because of an interrupt that never arrives as `InterruptedException` (it lands in the sealed-key read of a session boot hasn't reopened) → the same outcome: deleted, "not confirmed", and the interrupt handed back at the end (AC-113, AC-111; integration).
- A boot reopen of an account unlinked meanwhile → it is signed out, closed and destroyed in that order (unit). When the unlink already destroyed the directory, the reopen comes up signed out and the sign-out can't succeed; the Owner already got the "not confirmed" answer (AC-111, AC-113; integration).
- An unlink with an interrupt pending and no sign-out to take it (a Session lost account) → the account is still deleted, and the interrupt is handed back at the end (AC-113, AC-111; integration).
- Busy states while a request is in flight: SCR-10 and SCR-60 "Starting", SCR-60 "Unlinking" with "Keep account" disabled, SCR-02 "Sending code", "Checking the code" and "Checking the password" with the field read-only and Cancel disabled, SCR-02 "Sending a new code" with Continue and Cancel disabled, and "Starting" on the ended and wait cards. Each shows its label with `aria-busy` and holds the sibling actions (AC-01, AC-106, AC-114, AC-117, AC-111, AC-02, AC-109; component).
- Restart while an attempt is open → the attempt is gone (it is in memory), its session directory is swept, and SCR-02 shows "This linking attempt ended" (integration + component).
- Live-update stream reconnects → the SPA refetches everything it shows (component).
- A live-update stream from a Sign-in Session that ends → the stream closes and the next request routes to "Session ended" (integration + component).
- Sign in again on an account that is Connected → answered as already linked (sad §6 flow-10 flag; integration).

## Test data

- **Seed strategy:** the `data-model.md` §Test fixtures builders: `anOwnerKey(owner)`, `aLinkedAccount(owner, …)` (registers the same session in the fake adapter; `state = SESSION_LOST` and `afterMasterKeyReset()` variants) and `someChannels(account, count = 500, archived = 50)`. Owners come from the E01 builders. Fake Telegram accounts use the test-number shape `99966XYYYY`; separate numbers script two-step verification, wrong code, the wait, invalid, unknown and banned numbers, terminate and outage. Names are `Test User`, chats are `Test chat <n>`, and there is no real-looking data.
- **Integration dependency:** a throwaway Postgres container per suite, migrated by Flyway, plus a fresh temporary directory per test class as the session volume. The fake Telegram adapter is the port's in-memory implementation, not a mock of a datastore. No mocked datastore at this level.
- **Cleanup boundary:**
  - Integration: per test, by isolation. Each test makes its own Owners and unique Telegram user ids and asserts only on their rows, and the fake adapter is reset between tests. The "nothing left behind" and scan tests assert across every row, so they clear `linked_account`, `channel`, `owner_key` and `event_publication` first. Restart tests own their context and directory for the whole class.
  - E2E-through-UI: per test. Each test signs up a fresh unique address in its own browser context and uses its own fake numbers, so tests never share Owners or Telegram identities. The stack and the session volume are reset per run.
- **Clock:** unit and integration tests inject a fixed clock for the 15-minute expiry, the 5-minute Session lost window and the waits. E2E-through-UI uses the fake's immediate terminate and short waits, and moves the test-profile clock where a countdown needs it.

## NFR validation (load)

- **Linked Accounts per instance (≥ 50 connected at once, tech-spec NFR-04)** → scenario: on one instance with 4 vCPU / 8 GB, link 50 accounts on Telegram's test servers and hold them all connected for 30 min, with one restart in the middle. Assert that 50 of 50 are Connected at the end and within 60 s after the restart, and record resident memory and CPU per Telegram client against the sad §7 budget of 30–60 MB native memory per client. **Manual** one-off run, recorded in the E02 pull request.
- **Wizard step response (p95 ≤ 3 s per step)** → scenario: against the fake adapter, 20 concurrent Owners each run phone → code → password, repeated for 5 min. Assert the p95 of each step's duration (the `telex.linking.step.duration` metric, excluding code delivery) ≤ 3 s, with no failed steps. The manual check on a real test account is recorded in the E02 pull request.

The other numeric §6 NFRs are timed at other levels:

- Chat sync ≤ 60 s for ≤ 500 chats, and later changes within 60 s → integration with a 500-chat fake account (AC-116, AC-121), plus a **manual** timed run on a real test account recorded in the E02 pull request.
- Session lost ≤ 5 min, and never shown during an outage → integration with a controlled clock (AC-117, AC-122), plus a **manual** check on a test account.
- Reconnect ≤ 60 s after a restart → integration with 2 accounts (AC-36), plus a **manual** restart.
- Unlink announcement 100% delivered across a restart → integration (AC-112).
- Session data at rest 100% unreadable, and unlink leaves nothing → integration (QG-1a, AC-111, AC-113), plus the **manual** stored-data dump and Telegram active-sessions check recorded in the E02 pull request.
- 360 px and 1280 px, WCAG 2.2 AA → every e2e-through-UI test runs at both widths with an accessibility scan (0 violations).

## CI placement

- **On every PR:** unit, component, integration (including the restart, crash and 500-chat sync suites, which are this epic's DoD evidence), contract, and the existing `ModularityTest` and `MigrationRollbackIT`.
- **On every PR, as a separate job:** e2e-through-UI at both widths with the accessibility scan, as in E01.
- **Pre-release (before the E02 PR is merged):** the two load scenarios above and the manual real-Telegram checks (sync timing, Session lost, restart, stored-data dump and active sessions), recorded in the PR.
