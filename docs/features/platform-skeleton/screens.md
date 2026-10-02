---
status: draft            # draft | approved
feature_size: "M"
tool: "code"
updated_at: "2026-10-02"
---

# Screens — platform-skeleton

> The canonical **screen manifest** — every screen in every state — produced by `screens` (between
> `api` and `tasks`) and read by `tasks` (each `ui` task cites SCR ids + states), `implement`
> (builds the screen to the declared states) and `review` (the built screen must match this).
> Downstream stages reference **only this manifest** — never the raw Figma / `.pen` file.

## Source

- **Tool:** code. There is no `docs/design-system.md` canon yet, so the run defaults to `code` mode. This is not an MCP degradation; it's the documented default when the canon is missing. Run `/sdd:design-system` to set up a canon.
- **File:** the wireframes are inline below.
- **Component inventory:** `docs/docs/design-system/`, which holds the teleX components (`components/*/README.md`, typed in `components/index.d.ts`) and the Tabler 1.6 primitives the README builds on (`card`, `form-control`, `alert`, `list-group`, `navbar`, `page-center`, `spinner-border`). "Tabler: x" below means a Tabler primitive styled by `tokens.json`.
- **Reference mockups** (for humans; downstream needs only this manifest):
  - `docs/teleX-screens/Onb01-SignIn.html` is the same as `docs/docs/design-system/preview/Onb01-SignIn.html`. It is the visual source for SCR-01 `default`, and for the auth layout that SCR-07, SCR-08 and SCR-09 share.
  - `docs/teleX-screens/Inbox.html` and `PhoneInbox.html` show the post-E06 Inbox inside `AppShell`. They are not the E01 SCR-10 and are cited only as the target that replaces `PageFrame`.
  - `docs/teleX-screens/Empty.html` (cited in `spec.md` and the same as `preview/Empty.html`) is SCR-80 Overview on its first day, inside `AppShell`. It is the pattern source for SCR-10 `default`: an `EmptyState kind="first"` with one sentence and one action inside a surface card. Its frame and content (Overview, KPI tiles) are not E01.
  - `docs/teleX-screens/Onb02-Telegram.html` shows `CodeInput` used inside an onboarding card (E02), which supports the `CodeInput` reuse on SCR-07.
  - `docs/designs/scr-80-overview/` covers SCR-80 only and isn't used here.
- **Sources for state derivation:** `spec.md` §5 (AC-33…AC-105), the `sad.md` §6 flows and their `alt`/`else` branches, the `contracts/openapi.yaml` error responses, and the `ux-flows.md` screen inventory.

## Shared conventions

These conventions apply to every screen below. Each screen table lists only what differs from them.

- **Auth layout.** SCR-01, SCR-07, SCR-08 and SCR-09 use Tabler `page-center` with a logo asset 96 px above a `card` that is at most 420 px wide, padded `space-4`, with `radius-lg` and `shadow-sm`, as in `Onb01-SignIn`. The logo is allowed here because the README permits it on sign-in pages. On phone the card fills the width inside a 16 px gutter and uses padding `space-3`.
- **Bare system layout.** SCR-91, SCR-92 and SCR-93 use the same centered card, but with the **text wordmark "teleX"** in place of the logo image: the README limits the logo to sign-in, onboarding and the collapsed sidebar. They never depend on a session or on `/api/v1/me`.
- **Signed-in frame.** SCR-10 and SCR-64 render inside `PageFrame` (NEW, see §New components).
- **Busy button.** Every action that calls the API shows its `Button` as `disabled` with a Tabler `spinner-border-sm` and keeps its label. Other controls in the same form become read-only. The button's text says what's happening, and that text is announced politely. This is noted under §New components as a `Button` prop extension.
- **Failure routing (fetch client, sad §8 "SPA failure handling").** This is not drawn per screen. Each screen's `error` row cites it.
  - `401 unauthenticated` → SCR-01, with the current path remembered (AC-101).
  - `401 session-ended` → SCR-92 (AC-93, AC-96).
  - No answer within 10 s, any `5xx` (including `503 mail-unavailable`), or `403 forbidden` → SCR-93, whose Retry repeats the request (AC-102). `403` (CSRF token missing or wrong) is not mapped in sad §8; this manifest treats it as a server failure (see §Noted gaps).
  - Field-level `400 validation-failed` and the screen-specific `410`, `422` and `401 passkey-rejected` responses are handled on the screen.
- **Copy.** All strings go in `frontend/src/messages.ts`, in sentence case, with no emoji and no exclamation marks. The copy below is the proposed English text; `<address>` is the address as typed for that email (AC-34).
- **Status never by color alone.** Every alert, refusal and badge pairs an icon with words.

## Screens

### SCR-01 — Sign in

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | Opening teleX signed out; the README address (AC-33); a protected page opened while signed out, with its path remembered (AC-101); after Sign out (AC-95); "Sign in again" on SCR-92 | Auth layout, Tabler `form-control` (label "Email", `type=email`, `autocomplete=email`), `Button` primary block, divider "or", `Button` secondary block with `icon="lock"`, `small` note | `Onb01-SignIn`; wireframe W-01a |
| validation | Submitting an address without a name, an @ or a domain with a dot: client rule plus `400 validation-failed` / `email-incomplete` (AC-83). No email is sent | Tabler `form-control is-invalid` + `invalid-feedback` with an `alert-circle` icon | W-01b |
| submitting | Valid address submitted. The email is sent inside the request (Flow US-01 request) | Busy `Button` ("Sending email"), field read-only | W-01a (busy) |
| passkey-waiting | "Sign in with a passkey" chosen while the browser's device check is open (Flow US-45 sign in) | Busy secondary `Button` ("Waiting for your device") | W-01a (busy) |
| passkey-failed | `401 passkey-rejected`: unknown or removed credential, or a bad signature (AC-92). A cancelled check, or no passkey on the device, returns to `default` silently | Tabler `alert` (danger-subtle, `alert-circle` icon) above the form | W-01c |
| error | `503 mail-unavailable`, `403`, or no answer within 10 s | Shared failure routing → SCR-93 | — |
| success | `201` → SCR-07 for this address; passkey `200` → the remembered page or SCR-10 (AC-89, AC-101) | — | — |
| empty | N/A: a form with no collection | — | — |

```text
W-01a  SCR-01 default (desktop 1280; phone 360 = same column, 16 px gutter)
                 [ teleX logo 96 ]
+------------------------------------------+
|            Sign in to teleX              |   h1
|  Your Telegram, with assistants you stay |   text-secondary
|            in charge of.                 |
|                                          |
|  Email                                   |
|  [ me@example.com                     ]  |
|  [        Email me a sign-in link     ]  |   primary, block
|  ---------------- or -----------------   |
|  [ (lock) Sign in with a passkey      ]  |   secondary, block
|  No passwords. The link works once and   |   small, secondary
|  expires in 15 minutes.                  |
+------------------------------------------+

W-01b  validation
|  Email                                   |
|  [ me@localhost                       ]  |   border danger
|  (!) Enter a complete email address,     |   invalid-feedback
|      like me@example.com.                |

W-01c  passkey-failed
|  +------------------------------------+  |
|  | (!) That passkey didn't work. Sign |  |   alert, danger-subtle
|  |     in with your email instead.    |  |
|  +------------------------------------+  |
|  Email  [ ...                         ]  |
```

### SCR-07 — Check your email

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | `201` from `requestSignInEmail`. The page holds this email's grant id and `<address>` (AC-82, AC-103) | Auth layout, `CodeInput` (6 digits; `state="input"`; auto-advance, paste fills every digit, one-time-code autofill; the `limited` state and `resendIn` timer aren't used), `Button` primary "Sign in", `Button` ghost "Send a new link", `small` note | W-07a |
| validation | Fewer than 6 digits submitted, or `400 validation-failed` / `code-format` (doesn't count as a wrong attempt) | `CodeInput` + Tabler `invalid-feedback` "Enter the 6-digit code from the email." | W-07a (inline) |
| submitting | Code submitted to `redeemSignInCode` | `CodeInput` read-only, busy `Button` ("Signing in") | W-07a (busy) |
| wrong-code | `422 sign-in-code-wrong` with `attemptsLeft` ≥ 1 (Flow US-01 code, "fewer than 5 wrong codes") | `CodeInput state="invalid"` + "That code is not right. {n} tries left." ("1 try left" when n = 1); focus moves to the first digit | W-07b |
| refused-expired | `410 sign-in-link-expired`: over 15 minutes, superseded by a newer email, or an unknown grant id (AC-35, AC-103) | Card content replaced: h1 "This link has expired", `EmptyState kind="blocked"` (icon `clock`) "Send a new link to `<address>` to sign in.", action "Send a new link" | W-07c |
| refused-used | `410 sign-in-link-used` (AC-84; AC-82: the link is dead once the code was used, and the reverse) | Same as above: h1 "This link was already used", icon `ban` | W-07c |
| refused-void | `410 sign-in-grant-void`: 5 wrong codes, whether this was the 5th or a later try (AC-85) | Same as above: h1 "This code is no longer valid", icon `ban`, "Request a new email to `<address>` to sign in.", action "Send a new link" | W-07c |
| resending | "Send a new link" chosen from `default` or any refused state (`requestSignInEmail` with the same address) | Busy ghost/primary `Button` ("Sending email") | — |
| resent | `201` → a fresh SCR-07 for the new grant (AC-103: only the newest email works) | `default` + Tabler `alert` info-subtle (`info-circle`) "We sent a new email to `<address>`. Only the newest email works." | W-07d |
| no-grant | SCR-07 opened without a grant id (page reload, direct address); see §Noted gaps | Redirects to SCR-01 | — |
| error | `503 mail-unavailable` on resend, `403`, or no answer within 10 s | Shared failure routing → SCR-93 | — |
| success | `200 SignedIn`: if `createdAccount` → SCR-09; otherwise the remembered page or SCR-10 (sad §6 "landing after sign-in") | — | — |
| empty | N/A: a form with no collection | — | — |

```text
W-07a  SCR-07 default
                 [ teleX logo 96 ]
+------------------------------------------+
|            Check your email              |   h1
|  We sent a sign-in link and a 6-digit    |
|  code to anton+work@example.com. Open    |
|  the link, or type the code here.        |
|                                          |
|  Sign-in code                            |
|  [ ][ ][ ][ ][ ][ ]                      |   CodeInput
|  [               Sign in              ]  |   primary, block
|  [           Send a new link          ]  |   ghost, block
|  The link and the code work once and     |   small, secondary
|  expire in 15 minutes.                   |
+------------------------------------------+

W-07b  wrong-code
|  [4][8][2][0][1][9]                      |   CodeInput invalid
|  (!) That code is not right. 3 tries     |
|      left.                               |

W-07c  refused-* (expired shown; used / void swap the h1, icon and sentence)
+------------------------------------------+
|          This link has expired           |   h1
|                 (clock)                  |   EmptyState blocked
|   Send a new link to                     |
|   anton+work@example.com to sign in.     |
|          [ Send a new link ]             |   primary
+------------------------------------------+

W-07d  resent
|  +------------------------------------+  |
|  | (i) We sent a new email to anton+  |  |   alert, info-subtle
|  |     work@example.com. Only the     |  |
|  |     newest email works.            |  |
|  +------------------------------------+  |
|  ...default content...                   |
```

### SCR-08 — Confirm sign-in link

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| loading | Link opened; `previewSignInLink` reads the grant without redeeming it (AC-86) | Auth layout + `LoadState state="loading"` (`rows=2`) inside the card | — |
| default | `200 SignInLinkPreview`, still usable when opened. Nothing happens until the click, so a mail scanner or link preview never signs anyone in (AC-86) | h1 "Sign in to teleX", "Confirm to sign in on this device.", `Button` primary block "Continue as `<address>`", `small` "Nothing happens until you continue." | W-08a |
| submitting | "Continue as" chosen; `redeemSignInLink` with the browser time zone and the session it already holds (AC-104) | Busy `Button` ("Signing in") | W-08a (busy) |
| refused-expired / refused-used / refused-void | `410` on **open or on confirm**, because the 15 minutes count at the confirm (AC-35, AC-84, AC-85, AC-103) | Same layout and copy as SCR-07 `refused-*` (`EmptyState kind="blocked"` + "Send a new link"), using the `email` from the problem response | W-07c |
| link-unusable | `410` without `email` (unknown token), or `400 validation-failed` (`linkToken` missing from the URL fragment) | h1 "This link can't be used", `EmptyState kind="blocked"` (icon `ban`) "Open the newest email from teleX, or sign in again.", action "Back to sign in" → SCR-01 | W-08b |
| resending | "Send a new link" chosen | Busy `Button`; `201` → SCR-07 for the same address, in this browser | — |
| error | `503 mail-unavailable` on resend, `403`, or no answer within 10 s | Shared failure routing → SCR-93 | — |
| success | `200 SignedIn`: if `createdAccount` → SCR-09; otherwise the remembered page or SCR-10. A link confirmed in a different browser finds nothing remembered and lands on SCR-10 (AC-101) | — | — |
| empty | N/A: one record, no collection | — | — |

```text
W-08a  SCR-08 default
                 [ teleX logo 96 ]
+------------------------------------------+
|            Sign in to teleX              |   h1
|  Confirm to sign in on this device.      |
|  [ Continue as anton+work@example.com ]  |   primary, block (wraps on phone)
|  Nothing happens until you continue.     |   small, secondary
+------------------------------------------+

W-08b  link-unusable
+------------------------------------------+
|         This link can't be used          |   h1
|                  (ban)                   |   EmptyState blocked
|   Open the newest email from teleX, or   |
|   sign in again.                         |
|          [ Back to sign in ]             |   primary
+------------------------------------------+
```

### SCR-09 — Create a passkey

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | Reached right after a redeem answered `createdAccount: true`, on a browser that can create passkeys (AC-89, AC-91) | Auth layout, `Icon lock` (24 px), h1 "Sign in faster next time", "Create a passkey to sign in with your fingerprint, face or screen lock instead of an email.", `Button` primary "Create a passkey", `Button` ghost "Not now" | W-09a |
| unsupported | The browser can't create passkeys (AC-90) | h1 "Passkeys aren't available here", `Icon info-circle`, "This browser doesn't support passkeys. You can add one later from another device in Profile and security.", `Button` primary "Continue" → SCR-10 | W-09b |
| waiting | `passkeyRegistrationOptions` → device check → `registerPasskey` in progress | Busy primary `Button` ("Waiting for your device"); "Not now" disabled | W-09a (busy) |
| failed | Device check cancelled or failed, or `400 passkey-registration-failed` (AC-105) | Tabler `alert` danger-subtle (`alert-circle`) "No passkey was created. Try again, or choose Not now."; primary label becomes "Try again"; "Not now" stays | W-09c |
| no-flag | SCR-09 opened without the `createdAccount` flag (reload, direct address); sad §6 "Passkey step is derived, not stored" | Redirects to SCR-10 | — |
| success | `200 PasskeyRegistered` → SCR-10. The passkey appears on SCR-64 as "Never used" (AC-89) | — | — |
| error | `401` (via the fetch client) → SCR-01 or SCR-92; `5xx`, `403` or no answer within 10 s → SCR-93 | Shared failure routing | — |
| empty | N/A: a single step | — | — |

"Not now" from `default` or `failed` goes to SCR-10, and the step never comes back on any device (AC-91).

```text
W-09a  SCR-09 default
                 [ teleX logo 96 ]
+------------------------------------------+
|                  (lock)                  |
|         Sign in faster next time         |   h1
|  Create a passkey to sign in with your   |
|  fingerprint, face or screen lock        |
|  instead of an email.                    |
|  [          Create a passkey          ]  |   primary, block
|  [               Not now              ]  |   ghost, block
+------------------------------------------+

W-09b  unsupported
|              (info-circle)               |
|      Passkeys aren't available here      |   h1
|  This browser doesn't support passkeys.  |
|  You can add one later from another      |
|  device in Profile and security.         |
|  [              Continue              ]  |   primary, block

W-09c  failed
|  +------------------------------------+  |
|  | (!) No passkey was created. Try    |  |   alert, danger-subtle
|  |     again, or choose Not now.      |  |
|  +------------------------------------+  |
|  [              Try again             ]  |   primary
|  [               Not now              ]  |   ghost
```

### SCR-10 — Inbox (empty)

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| loading | Opening SCR-10; `GET /api/v1/me` in flight | `PageFrame` + `LoadState state="loading"` | — |
| default (empty) | `Me.linkedAccountCount = 0`, which is always the case before E02 (AC-100, AC-34). E01's default *is* the empty state | `PageFrame`, h1 "Inbox", `EmptyState kind="first"` (icon `brand-telegram`) "Connect your Telegram account to start.", action "Connect Telegram" | W-10a |
| note | "Connect Telegram" chosen; spec §8 OQ default | `Toast` kind info "Telegram linking is coming next." (dismisses itself, announced politely; choosing the action again shows it again) | W-10a (toast) |
| with-account | N/A before E02: `linkedAccountCount` is always 0, and the full Inbox (`docs/teleX-screens/Inbox.html`) is a later epic | — | — |
| error | `401 unauthenticated` → SCR-01 (path remembered); `session-ended` → SCR-92; `5xx` or no answer within 10 s → SCR-93 | Shared failure routing | — |

```text
W-10a  SCR-10 default (desktop)
+--------------------------------------------------------------------+
| teleX              (user) Profile and security   (logout) Sign out |  PageFrame
+--------------------------------------------------------------------+
|  Inbox                                                             |   h1
|  +--------------------------------------------------------------+  |
|  |                     (brand-telegram)                         |  |   EmptyState first
|  |          Connect your Telegram account to start.             |  |
|  |                   [ Connect Telegram ]                       |  |   primary
|  +--------------------------------------------------------------+  |
|                                  +--------------------------------+|
|                                  | (i) Telegram linking is coming ||   Toast info
|                                  |     next.                      ||   (after the action)
|                                  +--------------------------------+|
+--------------------------------------------------------------------+

phone 360: header = "teleX" + icon-only (user) (logout) buttons with ariaLabel,
           44 px targets; the card fills the width inside a 16 px gutter.
```

### SCR-64 — Profile and security

The page loads `GET /api/v1/me`, `GET /api/v1/passkeys` and `GET /api/v1/sessions`. The two cards load and fail independently. Records come only from the Owner's own data (AC-97). The sessions card has the anchor `#sessions`, which the link in the "New sign-in to teleX" email targets (AC-98). A signed-out visitor goes to SCR-01 and returns here afterwards (AC-101).

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | Passkeys and sessions loaded | `PageFrame`, h1 "Profile and security", `text-secondary` "Signed in as `<Me.email>`" (where "New sign-in to teleX" emails go, AC-34), then two Tabler `card`s, each with a `list-group` | W-64a |
| passkeys-loading | `GET /passkeys` in flight | `LoadState state="loading"` (`rows=2`) in the Passkeys card | — |
| passkeys-list | ≥ 1 passkey | Row: h4 label ("Safari on iPhone"), `small` "Created 2 Oct 2026 · Last used 12 min ago", or "Never used" when `lastUsedAt` is null (AC-89); `Button` ghost `icon="trash"` "Remove". Card header: `Button` secondary `icon="plus"` "Add a passkey" | W-64a |
| passkeys-empty | 0 passkeys (AC-91) | `EmptyState kind="first"` (icon `lock`) "No passkeys yet.", action "Add a passkey"; the header button is hidden, so the card has one action | W-64b |
| passkeys-unsupported | This browser can't create passkeys (by analogy with AC-90; no direct AC) | Every "Add a passkey" is replaced by `small` with `Icon info-circle`: "This browser doesn't support passkeys. Add one from another device." The list or the empty sentence still shows | W-64b (variant) |
| adding | "Add a passkey": options → device check → register (Flow US-46 manage passkeys) | Busy "Add a passkey" `Button` ("Waiting for your device") | — |
| add-cancelled | Device check cancelled | Back to the previous state, no message (Flow US-46: "SCR-64 unchanged") | — |
| add-failed | `400 passkey-registration-failed` | `Toast` kind error "No passkey was created. Try again." (errors stay until dismissed) | — |
| added | `200 PasskeyRegistered` | The passkey list refetches; the new row shows "Never used" | — |
| remove-confirm | "Remove" on a row, including the only passkey (AC-92) | `ConfirmDialog variant="default"`: title "Remove this passkey?"; consequence "“Safari on iPhone” will no longer sign you in. Sessions already open stay open. If you lost this device, also end its session in Sign-in sessions."; confirm "Remove passkey" (`danger`); cancel "Keep passkey" | W-64c |
| removing | Confirmed; `DELETE /passkeys/{id}` | Busy confirm `Button` ("Removing") | — |
| removed | `204`, or `404` (not among this Owner's passkeys, AC-97) | Dialog closes and the list refetches; the row is gone. For `404` nothing else is shown | — |
| sessions-loading | `GET /sessions` in flight | `LoadState state="loading"` (`rows=2`) in the Sessions card | — |
| sessions-list | ≥ 2 sessions (AC-93) | Row: h4 `userAgentLabel` ("Chrome on Mac"), `small` "{Phone \| Tablet \| Computer \| Unknown device} · Active 12 min ago" (relative under a day, a date after). Current row: `Badge tone="neutral" icon="check"` "This device", with no End button. Other rows: `Button` ghost "End session". Footer: `Button` secondary "Sign out of all other sessions" | W-64a |
| sessions-only-this | Only the current session (AC-94 result) | The "This device" row only; the footer button is hidden | W-64d |
| ending | "End session" chosen (no confirm, per spec) | That row's `Button` busy ("Ending") | — |
| ended | `204`, or `404` (not among this Owner's sessions, AC-97) | Sessions list refetches; the row is gone. That device's next action lands on SCR-92 | — |
| ending-others | "Sign out of all other sessions" (`POST /sessions/end-others`) | Footer `Button` busy ("Signing out other sessions") → `204` → `sessions-only-this` | — |
| sessions-empty | N/A: the current session is always listed | — | — |
| error | `401 unauthenticated` → SCR-01, then back to SCR-64 (AC-101); `session-ended` → SCR-92; `5xx`, `403` or no answer within 10 s → SCR-93 | Shared failure routing | — |

```text
W-64a  SCR-64 default (desktop; on phone the cards stack full-width and row actions drop below the text)
+--------------------------------------------------------------------+
| teleX              (user) Profile and security   (logout) Sign out |  PageFrame
+--------------------------------------------------------------------+
|  Profile and security                                              |   h1
|  Signed in as anton@example.com                                    |   text-secondary
|                                                                    |
|  +- Passkeys ------------------------------- [ + Add a passkey ] -+ |   card h2
|  | Safari on iPhone                                  [ Remove ]  | |
|  | Created 2 Oct 2026 · Never used                               | |
|  |---------------------------------------------------------------| |
|  | Chrome on Mac                                     [ Remove ]  | |
|  | Created 28 Sep 2026 · Last used 12 min ago                    | |
|  +---------------------------------------------------------------+ |
|                                                                    |
|  +- Sign-in sessions (#sessions) ---------------------------------+ |
|  | Chrome on Mac   [check This device]                           | |   Badge neutral
|  | Computer · Active now                                         | |
|  |---------------------------------------------------------------| |
|  | Safari on iPhone                             [ End session ]  | |
|  | Phone · Active 3 h ago                                        | |
|  |---------------------------------------------------------------| |
|  |                       [ Sign out of all other sessions ]      | |   footer, secondary
|  +---------------------------------------------------------------+ |
+--------------------------------------------------------------------+

W-64b  passkeys-empty (and its unsupported variant)
|  +- Passkeys --------------------------------------------------+ |
|  |                        (lock)                               | |   EmptyState first
|  |                  No passkeys yet.                           | |
|  |                [ Add a passkey ]                            | |
|  |   unsupported: (i) This browser doesn't support passkeys.   | |   replaces the button
|  |                    Add one from another device.             | |
|  +-------------------------------------------------------------+ |

W-64c  remove-confirm (ConfirmDialog, shadow-lg; full-width sheet on phone)
+----------------------------------------------+
|  Remove this passkey?                        |   h3
|  "Safari on iPhone" will no longer sign you  |
|  in. Sessions already open stay open. If you |
|  lost this device, also end its session in   |
|  Sign-in sessions.                           |
|            [ Keep passkey ] [ Remove passkey ] |   secondary + danger
+----------------------------------------------+

W-64d  sessions-only-this
|  +- Sign-in sessions ------------------------------------------+ |
|  | Chrome on Mac   [check This device]                         | |
|  | Computer · Active now                                       | |
|  +-------------------------------------------------------------+ |
```

### SCR-91 — Page not found

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | Any teleX address the SPA router doesn't know (AC-102) | Bare system layout, h1 "Page not found", `EmptyState kind="none"` (icon `search`) "This page doesn't exist in teleX.", action "Go to Inbox" → SCR-10 | W-91 |
| signed-out | The same address opened while signed out | Same render; "Go to Inbox" → SCR-10 → `401` → SCR-01 with `/inbox` remembered (AC-101) | W-91 |
| loading / error / empty | N/A: a static page that requests no data | — | — |

### SCR-92 — Session ended

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | Any request answered `401 session-ended`: ended elsewhere (AC-93, AC-94), idle 30 days, or started 90 days ago (AC-96) | Bare system layout, h1 "Session ended", `EmptyState kind="blocked"` (icon `logout`) "Your session has ended. Sign in again to continue.", action "Sign in again" → SCR-01. On entry the TanStack cache is cleared, so no Owner data is left on screen or in Back history (as in sad §8 sign-out) | W-92 |
| loading / error / empty | N/A: a static page that requests no data | — | — |

### SCR-93 — teleX is unavailable

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | An action in a teleX tab that has already loaded gets no answer within 10 s, a `5xx` (including `503 mail-unavailable`), or `403` (AC-102; Flow US-01 request, "mail server unavailable"; Flow US-46 sign out, failure) | Bare system layout, h1 "teleX is unavailable", `EmptyState kind="blocked"` (icon `wifi-off`) "teleX didn't answer. Check your connection, then try again.", action `Button` primary `icon="refresh"` "Retry" | W-93 |
| retrying | Retry chosen; the failed request is repeated as-is | Busy `Button` ("Retrying") | W-93 (busy) |
| retry-failed | The repeat fails again (Flow US-49, "fails again → stays on SCR-93") | Same as `default`, plus `small` "Still no answer." in an `aria-live="polite"` region, so the repeat failure is visible and announced | W-93 |
| success | The repeat is answered | Back to the originating page, showing the result | — |
| cold-open | N/A: opening teleX from scratch while it's down shows the browser's own error page; no teleX page is involved (AC-102) | — | — |
| empty | N/A: no collection | — | — |

```text
W-91 / W-92 / W-93  bare system layout (the three differ only in h1, icon, sentence, action)
                    teleX                      text wordmark, not the logo image
+------------------------------------------+
|           teleX is unavailable           |   h1   | Page not found   | Session ended
|                (wifi-off)                |   icon | (search)         | (logout)
|   teleX didn't answer. Check your        |        | This page doesn't| Your session has
|   connection, then try again.            |        | exist in teleX.  | ended. Sign in
|                                          |        |                  | again to continue.
|            [ (refresh) Retry ]           |        | [ Go to Inbox ]  | [ Sign in again ]
|            Still no answer.              |   retry-failed only (aria-live)
+------------------------------------------+
```

## New components

| Component | Why no existing primitive fits | Registered in design-system |
|---|---|---|
| `PageFrame` | The signed-in frame for E01 (SCR-10, SCR-64): a Tabler `navbar` with the text wordmark "teleX" (linking to SCR-10) on the left, and `Button` ghost `icon="user"` "Profile and security" plus `Button` ghost `icon="logout"` "Sign out" on the right. On phone they become icon-only buttons with `ariaLabel` and 44 px targets. Sign out is busy while it runs; failure → SCR-93 and the session stays live. `AppShell` (C-01) doesn't fit: it requires the seven sections, Stop all (C-03) and the account switcher (C-02), none of which exist before E06, and `ux-flows.md` says "no navigation shell in E01". **Temporary: E06 replaces it with `AppShell`.** | pending |
| `Button` — `busy` prop (extension, not a new component) | `ButtonProps` has `disabled` but no in-progress state, and every API action here needs one. Adds a `busy?: boolean` prop: disabled, a Tabler `spinner-border-sm` before the label, and `aria-busy`. The label text can change ("Signing in") | pending |

## Noted gaps

The pipeline should close these. Each one has a default that `implement` follows until it's decided.

1. **Where SCR-07 keeps the grant id across a reload** isn't decided in `sad.md`. Default: none is kept, and SCR-07 without a grant redirects to SCR-01 (`no-grant`). The Sign-in Link in the email still works. Owner: Architect, before `/sdd:tasks`.
2. **`403 forbidden` (CSRF) handling** isn't in sad §8 "SPA failure handling". Default: treated as a server failure → SCR-93; Retry re-reads the `XSRF-TOKEN` cookie. Owner: Architect, before `/sdd:tasks`.
3. **Six new SCR ids** (SCR-07, SCR-08, SCR-09, SCR-91, SCR-92, SCR-93) still need adding to `docs/docs/03-product-spec.md`, as `ux-flows.md` already flags. Owner: PM.
