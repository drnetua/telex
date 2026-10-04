---
status: draft            # draft | approved
feature_size: "M"
tool: "code"
updated_at: "2026-10-03"
---

# Screens — telegram-link

> The canonical **screen manifest**: every screen in every state. `screens` produces it between
> `api` and `tasks`. `tasks` reads it (each `ui` task cites SCR ids + states), `implement` builds
> each screen to the declared states, and `review` checks the built screen against it.
> Downstream stages reference **only this manifest**, never the raw Figma / `.pen` file.

## Source

- **Tool:** code, as chosen in the `docs/design-system.md` canon. No MCP is involved, so nothing was degraded.
- **File:** the wireframes are inline below.
- **Component inventory:** the `docs/design-system.md` §Component inventory (built components in `frontend/src/components/`) and the reference components it lists. "Tabler: x" means a Tabler 1.6 primitive styled by `tokens.json`, as in platform-skeleton.
- **Reference mockup** (for humans; downstream needs only this manifest): `docs/teleX-screens/Onb02-Telegram.html` is the visual source for the SCR-02 card, its intro copy and the code hint. Its "Step 2 of 6" indicator (`OnboardingSteps`, C-31) is **not** used, per the spec §1 deviation: onboarding steps 3–6 don't exist yet. Its "Open the teleX bot" button belongs to E17 and is not shown.
- **Sources for state derivation:**
  - `spec.md` §5 (AC-01…AC-122);
  - the `sad.md` §6 flows (Critical flows 1–3, Flows 4–13) and their `alt`/`else` branches;
  - the `contracts/openapi.yaml` responses and problem codes;
  - the `ux-flows.md` screen inventory (SCR-02, SCR-10, SCR-60) and its Status Banner node.
- **Contract change made here:** `LinkingAttempt.codeLength` was added to `contracts/openapi.yaml`, so that `CodeInput` sizes itself to the code Telegram sent. It's recorded in `api-sync-report.md` §A.
- **Decisions confirmed with the user (2026-10-03):**
  - SCR-02 uses the onboarding card layout.
  - ~~Accounts is reached through a `PageFrame` "Accounts" button.~~ Superseded (T43/T45): Accounts is a Settings registry entry under E06's `AppShell`, see New components deviation.
  - NEW `LinkedAccountSummary` is shared by SCR-10 and SCR-60.
  - ~~E02 ports `StatusBanner` (C-04) now, and E06 extends it.~~ Superseded: E06 landed first and ported C-04; E02 only adds the condition.

## Shared conventions

These apply to every screen below. Each table lists only what differs from them. The platform-skeleton conventions (busy buttons, copy rules, status never by color alone) still hold.

- **Signed-in frame.** SCR-10 and SCR-60 render inside E06's `AppShell`. The `PageFrame` this document first specified was never built past E01 and is deleted (deviation, see New components). Accounts is not a header button: SCR-60 is the "Accounts" entry in the Settings registry (`settingsRegistry.ts`, path `/accounts`, owned by the Settings section), and SCR-10 lines link to it. The banner is the shell's C-04 condition (below), at the top of `AppShell`'s main column: under the header on phone, beside the side menu on desktop.
- **Onboarding card layout.** SCR-02 uses the platform-skeleton auth layout: Tabler `page-center`, the logo asset 96 px (the README allows the logo on onboarding), and a `card` at most 420 px wide, padded `space-4`, `radius-lg`, `shadow-sm`. On phone the card fills the width inside a 16 px gutter with padding `space-3`. Above the card sits the same pulse-fed `StatusBanner` as the shell (`PulseBanner` in `OnboardingLayout`).
- **Account disconnected banner** (not a screen; ux-flows "Status Banner node"; AC-122). Every signed-in screen (SCR-02, SCR-10, SCR-60, SCR-64) shows the shell's `StatusBanner` (C-04) whenever the `account-disconnected` condition is active. `SessionLostConditions`, a `StatusConditionSource` in `messaging`, reports it in the pulse while an account is `session_lost` (app-shell ADR-0006); the pulse alone decides whether it is active. `useConditionLives` (`shell/conditions.ts`) calls `useAccountDisconnected` (`shell/accountDisconnected.tsx`, app-shell ADR-0006 amendment), which fetches `listMyLinkedAccounts` only while it is reported, to name the account and offer the action: one account shows "Sign in again" as a banner `button` action that starts the attempt against it (`startMyLinkingAttempt` with the target, then SCR-02), several show "Open Accounts". Its outcome handlers and the refusal `Toast` live at hook level, so they survive the line being outranked or dropped. Info Toasts are announced through the persistent polite live region; the refusal is an error `Toast`, which is its own `role=alert` outside that region (`Toast.tsx`). A loaded list with no `session_lost` account (signed in again, last one unlinked) drops the banner at once; while the list is refetching it is not trusted. It has no close button and stays until no account is Session lost. Starting an attempt and an unlink invalidate the pulse. On SCR-02 a failed background pulse never replaces the wizard with SCR-93; it only drives the banner.
  - **One lost account:** icon `alert-circle` + "`<displayName>`'s Telegram is disconnected. teleX can't work with it until you sign in again." The action "Sign in again" calls `startMyLinkingAttempt` with `origin: accounts` and `targetLinkedAccountId`, and opens SCR-02. Refusals from that call follow SCR-60's `start-refused` row.
  - **Several lost accounts:** "`<n>` Telegram accounts are disconnected." The action "Open Accounts" goes to SCR-60.
  - **Priority:** `account-disconnected` is one entry in the shell's condition catalog (`conditions.ts`); the shell's `StatusBanner` owns ordering and "+N more", with importance set by app-shell ADR-0006 (below offline / not responding).
- **Live state.** The SPA keeps one `openLiveUpdates` stream per tab. On a `linked-accounts` hint it refetches `listMyLinkedAccounts` in the background (`X-Telex-Background: 1`), and on a stream reconnect it refetches everything it shows (ADR-0005). The refetch replaces the data in place: no loading skeleton and no Toast. Status changes in a row are announced politely (`aria-live="polite"` on the state badge), and progress ticks aren't announced.
- **Masked phone.** `+<countryCode> ••• ••<lastDigits>`, e.g. `+999 ••• ••00`. Only the country code and the last two digits are real (AC-01). The middle is fixed decoration, not the real digit count.
- **Failure routing** (as in platform-skeleton, through the fetch client):
  - `401 unauthenticated` → SCR-01 with the path remembered.
  - `401 session-ended` → SCR-92. Mid-wizard, the open attempt is then discarded by the server (AC-110, Flow 8).
  - No answer within 10 s, an unmapped `5xx`, or `403` → SCR-93.

  The E02-specific `503`s (`telegram-linking-not-set-up`, `telegram-unavailable`), and every `404`, `409`, `422` and `429` below, are handled on the screen.
- **Copy.** All strings go in `frontend/src/messages.ts` (new `messages.linking`, `messages.accounts` and `messages.banner` groups). The copy below is the proposed English text. `<displayName>` is the account's Telegram name, and `<limit>` is the problem's `limit`. Sentence case, no emoji, no exclamation marks.
- **Secrets on screen.** The phone field uses `type="tel"`, `autocomplete="tel"`, `inputmode="tel"`. The password field uses `type="password"` and `autocomplete="off"`, because it is a Telegram password, not a teleX one, so a password manager must not save it for teleX. None of the typed values survive a failed step except the phone number in its own field.

## Screens

### SCR-02 — Connect Telegram

SCR-02 is reached after `startMyLinkingAttempt` answered `200` / `201` on SCR-10, SCR-60 or the banner, or by loading its route directly (reload, another tab or device). On every load it calls `getMyLinkingAttempt` and renders the step that answers (AC-109). It never keeps a step in the browser. The card title is "Connect your Telegram" for a new link, and "Sign in again to `<displayName>`" plus the masked phone when `targetLinkedAccountId` is set; the name comes from `listMyLinkedAccounts`. Focus: a card that replaces a step (outcome or load-failed) focuses its heading; a step that replaces such a card focuses the card title (`tabIndex={-1}`), not on the first load. Every step shows a ghost `Button` "Cancel" at the bottom.

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| loading | Route opened; `getMyLinkingAttempt` in flight | Onboarding card + `LoadState state="loading"` (`rows=3`) | — |
| phone (default) | Attempt at `step: phone` (Flow 4, AC-01) | h1 card title; `text-secondary` "teleX signs in as you through Telegram's official library. Your session is encrypted with a key only your account holds."; Tabler `form-control` "Phone number" (tel, placeholder `+380 00 000 00 00`), `small` "Include the country code."; `Button` primary block "Send code"; `Button` ghost block "Cancel" | W-02a |
| phone-validation | Empty field on submit (client), or `400 validation-failed` on `phoneNumber` | Tabler `form-control is-invalid` + `invalid-feedback` (`alert-circle`) "Enter your phone number with the country code."; focus on the field | W-02b |
| phone-refused | `422` on `submitLinkingPhone`; attempt stays at the phone step (AC-107, Flow 5), except an unregistered number when no fresh Telegram session can be opened: the attempt ends, the answer is still `telegram-phone-unregistered` (shown here), and the SPA checks the attempt at once (`getMyLinkingAttempt`) and, on `404`, shows `attempt-ended` with the unregistered text (`ConnectTelegramPage.tsx`, `phoneRefused`). The field keeps the typed number until then | `is-invalid` + `invalid-feedback`, per code: `telegram-phone-invalid` "This isn't a valid phone number. Check the country code and the digits."; `telegram-phone-unregistered` "No Telegram account uses this number. Create the account in the Telegram app first, then come back."; `telegram-phone-banned` "Telegram has banned this number, so it can't be linked." | W-02b |
| code | Attempt at `step: code` (Flow 5 "code sent", Flow 6) | h1 card title; `text-secondary` "Telegram sent the code to your other devices, not by SMS. Open Telegram on your phone or computer."; `CodeInput` with `length = codeLength` (extension, see §New components); `Button` primary block "Continue"; `Button` ghost block "Send a new code"; `Button` ghost block "Cancel" | W-02c |
| code-validation | Fewer digits than `codeLength`, or `400 validation-failed` on `code` | `CodeInput` + Tabler `invalid-feedback` "Enter all `<codeLength>` digits of the code." | W-02c (inline) |
| code-wrong | `422 telegram-code-wrong` (AC-02, Flow 6) | `CodeInput state="invalid"` + "That code is not right. Try again, or send a new code."; the digits are cleared and focus goes to the first one | W-02d |
| code-expired | `422 telegram-code-expired` (AC-02) | `CodeInput state="invalid"` + "This code has expired. Send a new code."; the "Send a new code" `Button` becomes primary and "Continue" becomes secondary | W-02d |
| resending | "Send a new code" chosen; `resendLinkingCode`. A phone refusal on the resend (`422 telegram-phone-unregistered`, `-invalid` or `-banned`) ends the attempt (Flow 6, AC-107) | Busy ghost `Button` ("Sending a new code"); on a refusal → `attempt-ended` with that refusal's text | — |
| resent | `200` from `resendLinkingCode` | `code` state with the digits cleared + `Toast` info "Telegram sent a new code." | — |
| password | Attempt at `step: password` (Flow 1, Flow 7). Only for accounts with two-step verification | h1 card title; `text-secondary` "Your account has two-step verification. Enter the password you set in Telegram."; Tabler `form-control` "Password" (`type=password`); when `passwordHint` is set, `small` with `Icon info-circle` "Hint: `<passwordHint>`"; `Button` primary block "Continue"; `small` "Forgot your password? It can only be reset in the Telegram app."; `Button` ghost block "Cancel" | W-02e |
| password-validation | Empty field on submit, or `400 validation-failed` on `password` | `is-invalid` + `invalid-feedback` "Enter your two-step verification password." | W-02e (inline) |
| password-wrong | `422 telegram-password-wrong` (AC-106, Flow 7) | `is-invalid` + `invalid-feedback` "That password is not right."; the hint line stays (from the problem's `passwordHint`); the "only in the Telegram app" line is shown in body text, not `small`; the field is cleared and focused | W-02f |
| submitting | Any step submitted (`submitLinkingPhone` / `submitLinkingCode` / `submitLinkingPassword`); p95 ≤ 3 s per step (spec §6) | Busy primary `Button` ("Sending code" / "Checking the code" / "Checking the password"); fields read-only; "Cancel" disabled | W-02a (busy) |
| load-failed | `getMyLinkingAttempt` fails without a route (not 401, 5xx, connectivity or 403); a failed background pulse never gets here | Card content replaced: `EmptyState kind="blocked"` "We couldn't load your Telegram linking."; `Button` primary "Try again" (refetches `getMyLinkingAttempt`, then focus moves to the heading of the step that loads); `Button` ghost "Back" → origin. No Toast | — |
| telegram-unavailable | `503 telegram-unavailable` on any step; the attempt stays at its step (api §B gap 2). Exception: when Telegram had already authorized the sign-in, the attempt ends and its session is logged out (outcome `failed`), so the next call is `404` and the card is `attempt-ended` | `Toast` error "Telegram didn't answer. Check your connection and try again." (stays until dismissed); the step is unchanged and its input kept (except the password, which is cleared) | — |
| step-mismatch | `409 linking-step-mismatch`: another tab or device moved the attempt on (api §B gap 1) | The step from the problem's `step` is rendered via a refetch of `getMyLinkingAttempt`, + `Toast` info "This step was already completed in another window." | — |
| wait | `429 telegram-wait-required` at any step; the attempt has ended (AC-02, Flows 5–7) | Card content replaced: h1 "Too many attempts"; `EmptyState kind="blocked"` (icon `clock`) "Telegram asks you to wait. You can try again at `<retryAt, local HH:mm>`, in `<m:ss>`."; the countdown updates every second but is announced only at start and when it ends; action `Button` secondary "Back" → origin. When the countdown ends, the sentence becomes "You can try again now." and the action becomes primary "Start again". "Back" and "Start again" are distinct buttons (own `key`), so an Enter meant for Back never starts an attempt; if Back had focus at 0:00, focus moves to the card heading. "Start again" moves focus to the new step's heading | W-02g |
| refused-other-owner | `409 telegram-account-owned-by-another-owner` (AC-04; Flow 10) | Card content replaced: h1 "This account is linked elsewhere"; `EmptyState kind="blocked"` (icon `ban`) "This Telegram account is already linked to another teleX account. One Telegram account belongs to one person. teleX has signed out of it again."; action "Back" → origin | W-02h |
| refused-already-linked | `409 telegram-account-already-linked` (AC-108) | h1 "Already linked"; `EmptyState kind="blocked"` (icon `check`) "This Telegram account is already one of your accounts. teleX has signed out of the extra sign-in."; action "Back" → origin | W-02h |
| refused-limit | `409 linked-account-limit-reached` at the end of the wizard (AC-115, Flow 1) | h1 "Account limit reached"; `EmptyState kind="blocked"` (icon `ban`) "You've linked `<limit>` accounts, the most this installation allows. teleX has signed out of this one. Unlink an account to free a place."; action "Open Accounts" → SCR-60 | W-02h |
| refused-mismatch | `409 telegram-account-mismatch` on "Sign in again" (AC-117, Flow 10) | h1 "A different account"; `EmptyState kind="blocked"` (icon `ban`) "You signed in to a different Telegram account, not `<displayName>`. teleX has signed out of it. To use it, add it as a new account."; action "Back to Accounts" → SCR-60 | W-02h |
| attempt-ended | `404 linking-attempt-not-found` on load or on any step: cancelled elsewhere, 15 min without a step, finished in another tab, or teleX restarted (AC-109, Flow 8) | h1 "This linking has ended"; `EmptyState kind="blocked"` (icon `clock`) "It was cancelled, finished in another window, or left for 15 minutes."; action primary "Start again" (`startMyLinkingAttempt` with the last known origin, `inbox` if none; focus then moves to the phone step's heading) + ghost "Back" → origin. When a phone refusal (`telegram-phone-unregistered`, `-invalid`, `-banned`) ended the attempt at the code step (all three, on the real adapter too), on Send a new code, or at the phone step with no fresh session, the body is that refusal's text from the problem catalog instead (AC-107). A sign in again whose account was unlinked meanwhile also ends here (AC-117); its "Start again" retries once without the target (a plain add) when the server answers 404 `not-found` for it | W-02i |
| starting-again | "Start again" chosen | Busy `Button` ("Starting"). `201` → `phone`. Every refusal is the SCR-10 `start-refused` copy, shown as a `Toast` error over this card: `503 telegram-linking-not-set-up`, `503 telegram-unavailable`, `409 linked-account-limit-reached`. For a sign in again whose target is gone, a `404 not-found` retries once without the target (Flow 10) | — |
| cancelling | "Cancel" chosen; `cancelMyLinkingAttempt` | Busy ghost `Button` ("Cancelling") → `204` → origin, with no message (AC-109) | — |
| success | `200 LinkingStepResult` `linked` / `signed-in-again` (AC-01, AC-108, AC-114, AC-117) | → origin (SCR-10 for `inbox`, SCR-60 for `accounts`) + `Toast` info there: "`<displayName>` is connected. teleX is syncing its chats." / "`<displayName>` is connected again." | — |
| error | `401` → SCR-01 / SCR-92 (AC-110); unmapped `5xx`, `403` or no answer within 10 s → SCR-93 | Shared failure routing | — |
| empty | N/A: a wizard with no collection | — | — |

```text
W-02a  SCR-02 phone (desktop 1280; phone 360 = same column, 16 px gutter)
   [ StatusBanner slot — only while an account is Session lost ]
                 [ teleX logo 96 ]
+------------------------------------------+
|          Connect your Telegram           |   h1  ("Sign in again to Test User
|  teleX signs in as you through           |        +999 ••• ••00" for a target)
|  Telegram's official library. Your       |   text-secondary
|  session is encrypted with a key only    |
|  your account holds.                     |
|                                          |
|  Phone number                            |
|  [ +380 00 000 00 00                  ]  |   form-control, type=tel
|  Include the country code.               |   small
|  [              Send code             ]  |   primary, block
|  [                Cancel              ]  |   ghost, block
+------------------------------------------+

W-02b  phone-validation / phone-refused (unregistered shown)
|  Phone number                            |
|  [ +380 00 000 00 00                  ]  |   border danger
|  (!) No Telegram account uses this       |   invalid-feedback
|      number. Create the account in the   |
|      Telegram app first, then come back. |

W-02c  code (codeLength = 5)
|          Connect your Telegram           |
|  Telegram sent the code to your other    |
|  devices, not by SMS. Open Telegram on   |
|  your phone or computer.                 |
|                                          |
|  Login code                              |
|        [ ][ ][ ][ ][ ]                   |   CodeInput length=5
|  [              Continue              ]  |   primary
|  [           Send a new code          ]  |   ghost
|  [                Cancel              ]  |   ghost

W-02d  code-wrong (code-expired swaps the sentence and makes "Send a new code" primary)
|        [4][8][2][0][1]                   |   CodeInput invalid
|  (!) That code is not right. Try again,  |
|      or send a new code.                 |

W-02e  password
|  Your account has two-step verification. |
|  Enter the password you set in Telegram. |
|  Password                                |
|  [ ••••••••                           ]  |   type=password, autocomplete=off
|  (i) Hint: Test hint                     |   small, only when set
|  [              Continue              ]  |   primary
|  Forgot your password? It can only be    |   small
|  reset in the Telegram app.              |
|  [                Cancel              ]  |   ghost

W-02f  password-wrong
|  Password                                |
|  [                                    ]  |   border danger, cleared, focused
|  (!) That password is not right.         |   invalid-feedback
|  (i) Hint: Test hint                     |
|  Forgot your password? It can only be    |   body text (not small)
|  reset in the Telegram app.              |

W-02g  wait (countdown running → ended)
+------------------------------------------+
|            Too many attempts             |   h1
|                 (clock)                  |   EmptyState blocked
|   Telegram asks you to wait. You can     |
|   try again at 10:15, in 4:32.           |   → "You can try again now."
|               [ Back ]                   |   → [ Start again ] primary
+------------------------------------------+

W-02h  refused-* (other-owner shown; the others swap h1, icon, sentence, action)
+------------------------------------------+
|     This account is linked elsewhere     |   h1
|                  (ban)                   |   EmptyState blocked
|   This Telegram account is already       |
|   linked to another teleX account. One   |
|   Telegram account belongs to one        |
|   person. teleX has signed out of it     |
|   again.                                 |
|               [ Back ]                   |
+------------------------------------------+

W-02i  attempt-ended
|          This linking has ended          |   h1
|                 (clock)                  |
|   It was cancelled, finished in another  |
|   window, or left for 15 minutes.        |
|   [ Start again ]   [ Back ]             |   primary + ghost
```

### SCR-10 — Inbox

E02 replaces E01's placeholder with the real thing. "Connect Telegram" now starts linking (E01's `note` Toast "Telegram linking is coming next." is removed), and the page lists the Linked Accounts. The page reads `listMyLinkedAccounts`. `Me.linkedAccountCount` is no longer what drives it.

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| loading | `listMyLinkedAccounts` in flight | `AppShell` + `LoadState state="loading"` | — |
| default (empty) | 0 Linked Accounts (AC-01 precondition; AC-111 after the last unlink) | `AppShell`, h1 "Inbox", `EmptyState kind="first"` (icon `brand-telegram`) "Connect your Telegram account to start.", action `Button` primary "Connect Telegram" | W-10a |
| starting | "Connect Telegram" chosen; `startMyLinkingAttempt { origin: inbox }` | Busy `Button` ("Starting") | — |
| start-refused | `503 telegram-linking-not-set-up` (AC-119); `503 telegram-unavailable` (Telegram didn't answer the start) | `Toast` error, per code: `telegram-linking-not-set-up` "Telegram linking isn't set up on this installation yet. The person who runs teleX has to finish the setup."; `telegram-unavailable` "Telegram didn't answer. Check your connection and try again." The wizard doesn't open | — |
| resumed | `200` (an open attempt, e.g. from another tab or a Sign in again) or `201` | → SCR-02, which renders the attempt's own step and origin | — |
| with-accounts | ≥ 1 Linked Account (AC-01, AC-114) | `AppShell`, h1 "Inbox", Tabler `card` with a `list-group`: one `LinkedAccountSummary variant="line"` per account, each row a link to SCR-60. Nothing else in E02; the Inbox content is later epics | W-10b |
| linked | Arrived from SCR-02 `success` (origin `inbox`) | `with-accounts` + the SCR-02 success `Toast` | — |
| live | `linked-accounts` hint | Background refetch, rows update in place (Shared conventions) | — |
| banner | Pulse reports `account-disconnected` (an account is `session_lost`) | `StatusBanner` from the shell (Shared conventions); the account's name and the Sign in again action come from the list, fetched only while the condition is reported, and a loaded list with no `session_lost` account drops it | W-10b |
| start-refused-limit | N/A: the "Connect Telegram" step shows only with 0 accounts, and the limit is ≥ 1 | — | — |
| error | `401` → SCR-01 (path remembered) / SCR-92; `5xx` or no answer within 10 s → SCR-93 | Shared failure routing | — |

```text
W-10a  SCR-10 default (empty) — E01 layout, action now real
+--------------------------------------------------------------------+
| AppShell: side menu on desktop, header + bottom nav on phone (see app-shell screens.md) |
+--------------------------------------------------------------------+
|  Inbox                                                             |
|  +--------------------------------------------------------------+  |
|  |                     (brand-telegram)                         |  |   EmptyState first
|  |          Connect your Telegram account to start.             |  |
|  |                   [ Connect Telegram ]                       |  |
|  +--------------------------------------------------------------+  |
+--------------------------------------------------------------------+

W-10b  SCR-10 with-accounts, one account Session lost (desktop; phone: one column, the shell's header and bottom nav)
+--------------------------------------------------------------------+
| AppShell: side menu on desktop, header + bottom nav on phone (see app-shell screens.md) |
+--------------------------------------------------------------------+
| (!) Test User's Telegram is disconnected. teleX can't work   [Sign in again] |  StatusBanner
|     with it until you sign in again.                               |
+--------------------------------------------------------------------+
|  Inbox                                                             |
|  +--------------------------------------------------------------+  |
|  | Test User  +999 ••• ••00     [check Connected]  312 of 480 chats > | LinkedAccountSummary line
|  |                              [=========-----]                  |   Tabler progress
|  |--------------------------------------------------------------|  |
|  | Test User  +999 ••• ••01     [(!) Session lost]              > |  |
|  +--------------------------------------------------------------+  |
+--------------------------------------------------------------------+
```

### SCR-60 — Accounts

The page lists the Owner's Linked Accounts, oldest first (`listMyLinkedAccounts`). It is reached from the Settings section's "Accounts" entry, an SCR-10 line, the banner's "Open Accounts", or the end of an attempt started here.

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| loading | `listMyLinkedAccounts` in flight | `AppShell` + `LoadState state="loading"` (`rows=2`) | — |
| default (list) | ≥ 1 account (AC-114) | `AppShell`, h1 "Accounts", `text-secondary` "Telegram accounts teleX works with. Each one syncs its own chats."; Tabler `card` with header "Linked accounts" and `Button` secondary `icon="plus"` "Add account", and a `list-group` of `LinkedAccountSummary variant="row"`, each with its actions (below) | W-60a |
| row: connected-syncing | `state: connected`, `chatSync.completedAt` null (AC-01, AC-116) | `LinkedAccountSummary`: `Badge tone="success" icon="check"` "Connected"; with `chatsTotal` set, Tabler `progress` + `small` "Syncing chats: `<chatsSynced>` of `<chatsTotal>`"; while `chatsTotal` is null, an indeterminate `progress` + "Syncing chats"; action `Button` ghost `icon="unlink"` "Unlink" | W-60a |
| row: connected-synced | `state: connected`, `completedAt` set (AC-116, AC-121) | `Badge tone="success" icon="check"` "Connected"; `small` "`<chatsSynced>` chats" (follows joins and leaves, AC-121); "Unlink" | W-60a |
| row: reconnecting | `state: reconnecting` (AC-122) | `Badge tone="neutral" icon="refresh"` "Reconnecting"; `small` "Telegram can't be reached right now. teleX reconnects by itself."; the sync line keeps its last values; "Unlink" | W-60a |
| row: session-lost | `state: session_lost` (AC-117, AC-118, AC-122) | `Badge tone="danger" icon="alert-circle"` "Session lost"; `small` "The session was ended in Telegram. Sign in again to bring this account back with everything attached."; `Button` primary small "Sign in again"; "Unlink" | W-60a |
| empty | 0 accounts, reached directly (the last unlink goes to SCR-10 instead) | `EmptyState kind="first"` (icon `brand-telegram`) "No Telegram accounts linked yet.", action "Add account"; the header button is hidden | W-60b |
| starting | "Add account" (`origin: accounts`) or "Sign in again" (`origin: accounts`, `targetLinkedAccountId`) | Busy `Button` ("Starting") | — |
| start-refused | `startMyLinkingAttempt` refused before the wizard (Flow 4, AC-115, AC-119) | `Toast` error, per code: `linked-account-limit-reached` "You've linked `<limit>` accounts, the most this installation allows. Unlink an account to add another."; `telegram-linking-not-set-up` as in SCR-10 `start-refused`; `telegram-unavailable` as in SCR-10 `start-refused`; `telegram-account-already-linked` (Sign in again on an account that is no longer Session lost) "This account is already connected." + background refetch; `404 not-found` → background refetch, the row disappears with no message (AC-03) | — |
| resumed | `200` / `201` | → SCR-02 | — |
| unlink-confirm | "Unlink" on a row (AC-111) | `ConfirmDialog tone="danger"` (the ordinary C-33 variant, spec §1 deviation): title "Unlink `<displayName>`?"; consequence "teleX will sign out of this Telegram account and delete its session and the `<chatsSynced>` chats it synced. To use it in teleX again, you'll link it from the start."; confirm "Unlink account"; cancel "Keep account" | W-60c |
| unlinking | Confirmed; `unlinkMyLinkedAccount` (up to 10 s waiting for Telegram, Critical flow 2) | Busy confirm `Button` (`busyLabel` "Unlinking"); cancel disabled | — |
| unlinked | `200 { signOutConfirmed: true }` (AC-111, AC-112) | Dialog closes and the list refetches; the row is gone; `Toast` info "`<displayName>` is unlinked." If the list is now empty → SCR-10, which shows "Connect Telegram" again (AC-111) | — |
| unlinked-unconfirmed | `200 { signOutConfirmed: false }` (AC-113) | As `unlinked`, but the Toast is error kind (stays until dismissed): "`<displayName>` is unlinked and teleX deleted everything it kept. Telegram couldn't confirm the sign-out, so check Active sessions in the Telegram app and end teleX there if it's listed." | — |
| unlink-gone | `404 not-found` on unlink: another tab unlinked it, or it was never the caller's (AC-03) | Dialog closes and the list refetches; the row is gone; no message | — |
| linked | Arrived from SCR-02 `success` (origin `accounts`) | `default` + the SCR-02 success `Toast` | — |
| live | `linked-accounts` hint (Flows 9, 11, 12) | Background refetch; a row's badge, sync line and actions change in place; a row unlinked in another tab disappears | — |
| banner | Pulse reports `account-disconnected` (an account is `session_lost`) | `StatusBanner` from the shell, as on SCR-10 | W-60a |
| error | `401` → SCR-01 (path remembered) / SCR-92; `5xx`, `403` or no answer within 10 s → SCR-93 | Shared failure routing | — |

```text
W-60a  SCR-60 default (desktop; on phone the row actions drop below the text, full width)
+--------------------------------------------------------------------+
| AppShell: side menu on desktop, header + bottom nav on phone (see app-shell screens.md) |
+--------------------------------------------------------------------+
| (!) Test User's Telegram is disconnected. ...        [Sign in again] |  StatusBanner
+--------------------------------------------------------------------+
|  Accounts                                                          |   h1
|  Telegram accounts teleX works with. Each one syncs its own chats. |
|  +- Linked accounts ---------------------------- [ + Add account ] +
|  | Test User  +999 ••• ••00              [check Connected]        |  connected-syncing
|  | [==========--------]  Syncing chats: 312 of 480     [ Unlink ] |
|  |----------------------------------------------------------------|
|  | Test User  +999 ••• ••02              [check Connected]        |  connected-synced
|  | 57 chats                                            [ Unlink ] |
|  |----------------------------------------------------------------|
|  | Test User  +999 ••• ••03              [(refresh) Reconnecting] |  reconnecting
|  | Telegram can't be reached right now. teleX          [ Unlink ] |
|  | reconnects by itself.                                          |
|  |----------------------------------------------------------------|
|  | Test User  +999 ••• ••01              [(!) Session lost]       |  session-lost
|  | The session was ended in Telegram. Sign in again to bring this |
|  | account back with everything attached.                         |
|  |                            [ Sign in again ]        [ Unlink ] |
|  +----------------------------------------------------------------+
+--------------------------------------------------------------------+

W-60b  empty
|  +- Linked accounts ----------------------------------------------+
|  |                      (brand-telegram)                          |   EmptyState first
|  |              No Telegram accounts linked yet.                  |
|  |                     [ Add account ]                            |
|  +----------------------------------------------------------------+

W-60c  unlink-confirm (ConfirmDialog, shadow-lg; full-width sheet on phone)
+----------------------------------------------------+
|  Unlink Test User?                                 |   h3
|  teleX will sign out of this Telegram account and  |
|  delete its session and the 480 chats it synced.   |
|  To use it in teleX again, you'll link it from     |
|  the start.                                        |
|               [ Keep account ] [ Unlink account ]  |   secondary + danger
+----------------------------------------------------+
```

## Not shown in E02 (by design)

- `AccountSwitcher` (C-02): it chooses the account for the chats view, which arrives in E04.
- `OnboardingSteps` (C-31) and the "Open the teleX bot" button: spec §1 deviations; E03…E17 add the other steps.
- Anything for the Operator: they have no screen; US-53 is a README step (ux-flows Flow US-53). AC-120's "the Operator sees nothing" is checked on logs, metrics and config, not on a screen.

## New components

| Component | Why no existing primitive fits | Registered in design-system |
|---|---|---|
| `LinkedAccountSummary` | One Linked Account's name, masked phone, state `Badge` (connected / reconnecting / session lost, icon + words) and chat-sync line (Tabler `progress` + count, or the chat count once synced). The same state-to-presentation mapping is needed in two places: `variant="line"` (SCR-10, compact, the whole row links to SCR-60, no actions) and `variant="row"` (SCR-60, with an actions slot). No inventory component shows an account; `AccountSwitcher` (C-02) chooses one and is hidden with a single account. The badge region is `aria-live="polite"`, so live state changes are announced. | registered in `docs/design-system.md` |
| `StatusBanner` (C-04) — **ported reference**, not new | Ported from `docs/docs/design-system/components/StatusBanner/README.md`: one condition, or the most severe plus "+N more"; one action; no close button while its cause holds; icon + words; `role="status"`. E06 ported it into `AppShell` with the shell's condition catalog; E02 adds the account-disconnected condition with its live text and action (`accountDisconnected.tsx`) and the generic shell extension that needs (`button` action, `notice`, `inactive`, hook-level callbacks; app-shell ADR-0006 amendment). Already listed in the inventory as "not yet ported", so `implement` updates its row to the built file. | registered in `docs/design-system.md` |
| `CodeInput` — `length` prop (extension) | The built `CodeInput` is fixed at 6 digits for the E01 Sign-in Code. A Telegram login code has the length Telegram reports (`codeLength`, usually 5). Adds `length?: number` (default 6, so E01 is unchanged) and a `label` prop, because the built label text is the E01 "Sign-in code" string. The reference C-32 states "resend timer" and "Telegram rate limit" aren't used: Telegram's resend timing isn't in the contract, and a rate limit ends the attempt (`wait`). | registered in `docs/design-system.md` |
| ~~`PageFrame` — "Accounts" button + banner slot (extension)~~ | **Deviation (T43, T45):** not built. E06's `AppShell` landed first, so Accounts sits under Settings and the banner is the shell's `account-disconnected` condition (app-shell ADR-0006); `PageFrame` is deleted. | n/a |
| `Icon` — `unlink` (extension) | The "Unlink" action needs `IconUnlink` from `@tabler/icons-react`; it's added to the Icon subset. | registered in `docs/design-system.md` |

## Noted gaps

The pipeline should close these. Each one has a default that `implement` follows until it's decided.

1. **The two api sequence gaps** (`linking-step-mismatch`, `telegram-unavailable`) are drawn here as `step-mismatch` and `telegram-unavailable` on SCR-02. Both branches are now in sad §6 Flows 5–7 (T33).
2. **Toast vs StatusBanner on SCR-02 for `telegram-unavailable`.** It's treated as a failed action (error Toast), not a system-wide condition, because the account isn't linked yet and nothing else is affected. If E06's "offline" banner lands first, a browser that is offline shows that banner instead.
3. **Where the "Accounts" entry sits once E06 lands.** Closed: E06 landed first, and Accounts is a Settings registry entry (`/accounts`, owned by the Settings section) with no header button.
