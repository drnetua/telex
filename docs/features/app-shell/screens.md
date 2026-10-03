---
status: draft            # draft | approved
feature_size: "M"
tool: "code"
updated_at: "2026-10-03"
---

# Screens — app-shell

> The canonical **screen manifest** — every screen in every state — produced by `screens` (between
> `api` and `tasks`) and read by `tasks` (each `ui` task cites SCR ids + states), `implement`
> (builds the screen to the declared states) and `review` (the built screen must match this).
> Downstream stages reference **only this manifest** — never the raw Figma / `.pen` file.

## Source

- **Tool:** code (from `docs/design-system.md`). No MCP degradation, since `code` is the canon's choice.
- **File:** the wireframes are inline below. The E01 screens (SCR-01, -07, -08, -09, -10, -92, -93) are repeated here **in full**, with the E06 changes applied, so this manifest is self-contained. Their earlier versions are in `docs/features/platform-skeleton/screens.md`.
- **Component inventory:** `docs/design-system.md` §Component inventory. "Tabler: x" means a Tabler 1.6 primitive styled by `tokens.json`.
  - `AppShell` (C-01) and `StatusBanner` (C-04) are **ported** from `docs/docs/design-system/components/{AppShell,StatusBanner}/` (README, `index.d.ts`, `bundle.js`), adjusted as §Shell says.
  - `PageFrame` is deleted.
- **Reference mockups** (for humans only):
  - `docs/teleX-screens/Inbox.html` and `PhoneInbox.html` show the shell at desktop and phone widths.
  - `Dark.html` shows the dark theme.
  - `Onb01-SignIn.html` shows the auth layout.
  - `Empty.html` shows the empty-card pattern.
- **Sources for state derivation:**
  - `spec.md` §5 (AC-170…AC-186, AC-43, AC-07b; the E01 ACs for the repeated screens)
  - `sad.md` §6 (seed flows 1–3, Flows 4–8) and §8 (Failure routing, Connectivity, Theme, Time and timezone)
  - `contracts/openapi.yaml` (`getPulse`, `getMe`, `changeMyPreferences`, `saveDetectedTimeZone`, `listTimeZones`), plus the platform-skeleton contract for the E01 operations
  - the `ux-flows.md` inventory
- **Spec §8 OQs resolved here (user, 2026-10-03):**
  - Phone bar: **Inbox, Chats, Assistants, Tasks, More**. Overview, Runs and Settings sit under More.
  - Status Banner importance: **offline / not responding > account disconnected > bot blocked > consent needed > budget exhausted > all assistants paused > triage deferred**.

## Shared conventions

These apply to every screen below. Each screen table lists only what differs from them.

- **Shell frame.** Every signed-in screen (SCR-10, -64, -69, -94; SCR-95 is part of the shell) renders inside `AppShell` (§Shell). The page title is the content's h1.
- **Auth layout.** SCR-01, -07, -08 and -09 use Tabler `page-center` with a 96 px logo above a `card` that is at most 420 px wide, padded `space-4`, with `radius-lg` and `shadow-sm` (`Onb01-SignIn`). On phone the card fills the width inside a 16 px gutter, padded `space-3`. No shell.
- **Bare system layout.** SCR-91, -92 and -93 use the same centered card, with the **text wordmark "teleX"** in place of the logo. They never depend on a session or on `getMe`. No shell.
- **Theme everywhere.** Every page renders in the theme last used on this device from the first frame. `data-bs-theme` is set on `<html>` by the inline `index.html` script, and System is resolved through `prefers-color-scheme` (sad §8 Theme). Only signed-in screens switch to the account theme after `getMe` answers (AC-181).
- **Theme changes.** `ThemeSwitch` (§New components) is the one control that changes the theme. It sits on SCR-64 and in the shell (sidebar footer, More). All three apply the choice at once, remember it on this device, send `changeMyPreferences {theme}`, and revert with an error `Toast` + "Try again" if the save fails (AC-179, AC-182).
- **Dates.** Every date or time shown goes through `formatInstant(instant, Me.timeZone)` (sad §8). In E06 that means the Passkeys and Sessions dates on SCR-64 (AC-183).
- **Failure routing.** It isn't drawn per screen, and each screen's `error` row cites it.
  - **Inside the shell (narrowed, AC-176, ADR-0004, user decision 2026-10-03):**
    - `401 unauthenticated` → SCR-01, with the section remembered (AC-173).
    - `401 session-ended` → SCR-92.
    - **No answer** (2 s for the pulse, 10 s for calls), a **network error**, or **any `502`/`503`/`504`, whatever its body** → the Status Banner, and the screen stays.
    - Any other `5xx` (`500 internal-error`) and `403 forbidden` → SCR-93 with Retry.
    - Field-level `400 validation-failed` is handled on the screen.
  - **Outside the shell (auth and system pages, unchanged from E01):** the same `401` rules. No answer within 10 s, any `5xx` (including `503 mail-unavailable`) or `403` → SCR-93, because there is no banner to show (AC-176 covers shell screens only).
- **Busy button.** `Button busy`: disabled, `spinner-border-sm`, label kept or changed to the action in progress, `aria-busy`.
- **Error toasts with an action.** An error `Toast` may carry one action ("Try again") that repeats the failed request. It stays until dismissed or acted on (§New components: `Toast` `action` prop).
- **Copy.** All strings go in `frontend/src/messages.ts`: sentence case, no emoji, no exclamation marks. Section names, Coming soon sentences and banner texts live there too. The copy below is the proposed English text. `<address>` is the address as typed for that email.
- **Status never by color alone.** The current section, the counter, every banner, alert and refusal pair an icon or shape with words.

## Screens

### Shell — AppShell and StatusBanner (on every signed-in screen)

Cross-cutting, not an SCR. These are the frame states every signed-in screen inherits (AC-170, AC-43, AC-07b, AC-172…AC-179, AC-181, AC-182).

**AppShell port, against the reference C-01:**
- Sections come from `frontend/src/shell/sections.ts`, in the reference `NAV` order and icons: Overview `layout-dashboard`, Inbox `inbox`, Chats `messages`, Assistants `sparkles`, Runs `activity`, Tasks `checklist`, Settings `settings`.
- Links are real routes, not `#hash`.
- On phone the bar holds Inbox, Chats, Assistants, Tasks and **More**, with Overview, Runs and Settings under More (SCR-95). This replaces the reference's "first five; Tasks and Settings in the header menu", because AC-43 puts the rest under More.
- The header in E06 holds **no Stop all and no account switcher** (spec §3). On phone it shows the current section's name. On desktop there is no header bar until a header item exists, and the banner slot sits at the top of the main column.
- The desktop sidebar footer holds the Owner's email, `ThemeSwitch` (menu variant) and Sign out. On phone these are under More.

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| starting | `getMe` in flight after a page load. Nothing of the shell shows until a live session is confirmed (AC-173) | Page background in the device's last theme, `LoadState state="loading"` (`rows=3`) in a centered column, no navigation, no counter, no banner | W-S0 |
| default-desktop | Width ≥ 768 px, `getMe` 200 (AC-170) | `AppShell` side menu (240 px): brand "tX teleX", the seven sections in app-map order. Footer: `small` `Me.email`, `ThemeSwitch variant="menu"`, `Button` ghost `icon="logout"` "Sign out" | W-S1 |
| default-phone | Width < 768 px (AC-43) | `AppShell` phone: header with the section name, bottom nav with 5 items at least 44 × 44 px (icon above label), and the Inbox item with its counter always present | W-S2 |
| current-section | Any section open (AC-170, AC-171) | The active item gets `aria-current="page"`, a 3 px `primary` bar at its inline start (desktop) or top (phone), `primary-subtle` background and **bold** label: shape plus weight plus color. On phone, a section under More marks **More** as current | W-S1, W-S2 |
| counter-none | `Pulse.inboxCount = 0`, or before the first pulse answers (AC-174) | Inbox item with no number; the item stays in place | W-S1 |
| counter | `inboxCount` 1…99 (AC-174) | `tx-count` pill (Tabler `badge bg-primary`, tokens only) with the number and `aria-label` "{n} items need you". A change is announced politely once per change, not every pulse | W-S1 |
| counter-max | `inboxCount` > 99 (AC-174) | Pill "99+", `aria-label` "More than 99 items need you" | — |
| theme-switch | `getMe.theme` differs from the theme applied at first paint (AC-181, Flow 6) | One switch of `data-bs-theme`, no reload, no transition animation | — |
| theme-menu-open | Desktop: the footer `ThemeSwitch` chosen | Tabler `dropdown-menu` (opens upward) with three `menuitemradio` items, each icon plus word: `sun` Light, `moon` Dark, `device-desktop` System. The theme applied on this device is checked. Focus goes to it on open; ↑ ↓ Home End move within the menu, Escape closes it and returns focus, and Tab or focus leaving the menu closes it | W-S6 |
| theme-applied / theme-save-failed | A theme chosen in the shell (AC-179, AC-182) | As `ThemeSwitch` on SCR-64 (`theme-applied`, `theme-save-failed`): apply at once; on failure revert + error `Toast` "Your theme wasn't saved." with action "Try again" | — |
| section-loading | A lazily loaded section chunk is still downloading (sad §4) | `LoadState state="loading"` in the content area; the navigation stays | — |
| section-load-failed | The chunk can't download because there's no network or teleX isn't answering (AC-176) | Content: `EmptyState kind="blocked"` (icon `wifi-off`) "This section didn't load." with action "Try again". The banner explains why | W-S5 |
| banner-offline | The browser reports no network (AC-176, seed flow 2) | `StatusBanner` under the header (phone) or at the top of the main column (desktop): `warning-subtle`, `Icon wifi-off`, "You're offline. teleX will update when your connection is back." + `Button` secondary sm "Try again". `role="status"`, no close control (AC-178) | W-S3 |
| banner-not-responding | The network is up, but the pulse or a call got no answer, a network error, or a 502/503/504 (AC-176) | `StatusBanner` `warning-subtle`, `Icon cloud-off`, "teleX isn't responding." + "Try again" | W-S3 |
| banner-retrying | "Try again" pressed: one immediate pulse (AC-177) | "Try again" `Button busy` ("Trying again") | — |
| banner-still-down | That pulse failed too (AC-177) | Same banner, with the text replaced by "Still can't reach teleX. It keeps trying on its own." (announced politely). The screen content is kept, and nothing is cleared | W-S3 |
| banner-several | 2 or more conditions hold (AC-178) | Only the most important by the fixed order shows, plus `Button` link "+{n} more" (`aria-expanded`), which expands an inline list under the banner. Each row has icon, words and its own action | W-S4 |
| banner-unknown-code | The pulse reports a code the SPA catalog doesn't know (ADR-0006) | Not shown. It's logged once to the console | — |
| recovered | The pulse is answered again (AC-176) | The banner disappears without a message, paused queries refetch, and the current screen shows fresh data within 5 s | W-S1 |
| signing-out | "Sign out" chosen (AC-172) | Busy `Button` ("Signing out") → the cache is cleared, the pulse stops → SCR-01 | — |
| session-lost | The pulse or any call answers `401` (AC-173, seed flow 1) | The shell is dropped at once → SCR-01 (`unauthenticated`, section remembered) or SCR-92 (`session-ended`) | — |
| error | `500` or `403` on a call | Shared failure routing (inside the shell) → SCR-93 | — |
| empty | N/A: the shell itself holds no collection. Each section's page owns its empty state | — | — |

```text
W-S0  starting (both widths): no shell yet
+--------------------------------------------------+
|                                                  |
|            ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒             |   LoadState rows=3
|            ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒                    |   (theme last used here)
|            ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒                |
+--------------------------------------------------+

W-S1  default-desktop, Inbox current, counter 4 (1280)
+------------------+-------------------------------------------------+
| tX teleX         |                                                 |   no header bar in E06
|                  |  Inbox                                          |   page h1
|   Overview       |  +-------------------------------------------+  |
| ▌ Inbox      (4) |  |  page content                             |  |   ▌ = 3 px primary bar,
|   Chats          |  |                                           |  |   bold label, subtle bg,
|   Assistants     |  +-------------------------------------------+  |   aria-current
|   Runs           |                                                 |
|   Tasks          |                                                 |
|   Settings       |                                                 |
|                  |                                                 |
| test.user@       |                                                 |   footer: email,
| example.test     |                                                 |
| [(moon) Theme ▴] |                                                 |   ThemeSwitch menu,
| [(logout) Sign out]                                                |   Sign out
+------------------+-------------------------------------------------+

W-S2  default-phone, Inbox current (360)
+--------------------------------------+
| Inbox                                |   header: section name
+--------------------------------------+
|  page content (16 px gutter)         |
|                                      |
+--------------------------------------+
| ▔▔▔▔▔                                |   ▔ = 3 px primary bar on top of the current item
|(inbox) (msgs) (spark) (check) (dots) |   each target ≥ 44 × 44
| Inbox4  Chats  Assist.  Tasks   More |   counter pill on the Inbox icon
+--------------------------------------+

W-S3  banners (phone shown; desktop: same strip at the top of the main column)
+--------------------------------------+
| Inbox                                |
+--------------------------------------+
| (wifi-off) You're offline. teleX     |   warning-subtle, role=status,
| will update when your connection is  |   no close control
| back.                   [ Try again ]|
+--------------------------------------+
  not-responding: (cloud-off) teleX isn't responding.            [ Try again ]
  still-down:     (cloud-off) Still can't reach teleX. It keeps trying on its own. [ Try again ]

W-S4  banner-several (expanded; fixture conditions in E06 e2e)
+--------------------------------------------------------------------+
| (cloud-off) teleX isn't responding.   [+2 more ▾]     [ Try again ] |
|   (wifi-off) Work account is disconnected from Telegram. [Reconnect]|   rows: icon + words +
|   (currency-dollar) Monthly budget reached. ...    [ Raise budget ] |   own action (catalog)
+--------------------------------------------------------------------+

W-S5  section-load-failed (content area; navigation and banner stay)
|  +-------------------------------------------+  |
|  |               (wifi-off)                  |  |   EmptyState blocked
|  |       This section didn't load.           |  |
|  |             [ Try again ]                 |  |
|  +-------------------------------------------+  |

W-S6  theme-menu-open (desktop sidebar footer)
| +----------------------+ |
| | (sun)     Light      | |   dropdown-menu, menuitemradio
| | (moon)    Dark     ✓ | |   ✓ + aria-checked on the one applied here
| | (desktop) System     | |
| +----------------------+ |
| [(moon) Theme ▴]         |
```

### SCR-01 — Sign in

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | Opening teleX signed out; the README address (AC-33); **a section link opened while never signed in or signed out, with the section remembered (AC-173, Flow 5)**; after Sign out (AC-95, AC-172); "Sign in again" on SCR-92. Nothing of the shell shows (AC-173) | Auth layout, Tabler `form-control` (label "Email", `type=email`, `autocomplete=email`), `Button` primary block, divider "or", `Button` secondary block with `icon="lock"`, `small` note | W-01a |
| validation | An address without a name, an @ or a domain with a dot: client rule plus `400 validation-failed` / `email-incomplete` (AC-83). No email is sent | Tabler `form-control is-invalid` + `invalid-feedback` with an `alert-circle` icon | W-01b |
| submitting | Valid address submitted; the email is sent inside the request | Busy `Button` ("Sending email"), field read-only | W-01a (busy) |
| passkey-waiting | "Sign in with a passkey" chosen while the device check is open | Busy secondary `Button` ("Waiting for your device") | W-01a (busy) |
| passkey-failed | `401 passkey-rejected`: unknown or removed credential, or a bad signature (AC-92). A cancelled check, or no passkey on the device, returns to `default` silently | Tabler `alert` (danger-subtle, `alert-circle`) above the form | W-01c |
| error | `503 mail-unavailable`, `403`, or no answer within 10 s | Shared failure routing (outside the shell) → SCR-93 | — |
| success | `201` → SCR-07 for this address. Passkey `200` → **the remembered section, cleared on use, or SCR-10 when there's none** (AC-89, AC-173) | — | — |
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
| default | `201` from `requestSignInEmail`; the page holds this email's grant id and `<address>` (AC-82, AC-103) | Auth layout, `CodeInput` (6 digits, auto-advance, paste fills every digit, one-time-code autofill), `Button` primary "Sign in", `Button` ghost "Send a new link", `small` note | W-07a |
| validation | Fewer than 6 digits, or `400 validation-failed` / `code-format` (not a wrong attempt) | `CodeInput` + Tabler `invalid-feedback` "Enter the 6-digit code from the email." | W-07a (inline) |
| submitting | Code submitted to `redeemSignInCode` | `CodeInput` read-only, busy `Button` ("Signing in") | W-07a (busy) |
| wrong-code | `422 sign-in-code-wrong` with `attemptsLeft` ≥ 1 | `CodeInput state="invalid"` + "That code is not right. {n} tries left." ("1 try left" when n = 1); focus moves to the first digit | W-07b |
| refused-expired | `410 sign-in-link-expired`: over 15 minutes, superseded, or an unknown grant id (AC-35, AC-103) | Card content replaced: h1 "This link has expired", `EmptyState kind="blocked"` (icon `clock`) "Send a new link to `<address>` to sign in.", action "Send a new link" | W-07c |
| refused-used | `410 sign-in-link-used` (AC-84, AC-82) | As above: h1 "This link was already used", icon `ban` | W-07c |
| refused-void | `410 sign-in-grant-void`: 5 wrong codes (AC-85) | As above: h1 "This code is no longer valid", icon `ban`, "Request a new email to `<address>` to sign in.", action "Send a new link" | W-07c |
| resending | "Send a new link" chosen from `default` or any refused state | Busy `Button` ("Sending email") | — |
| resent | `201` → a fresh SCR-07 for the new grant (AC-103) | `default` + Tabler `alert` info-subtle (`info-circle`) "We sent a new email to `<address>`. Only the newest email works." | W-07d |
| no-grant | SCR-07 opened without a grant id (reload, direct address) | Redirects to SCR-01 | — |
| error | `503 mail-unavailable` on resend, `403`, or no answer within 10 s | Shared failure routing (outside the shell) → SCR-93 | — |
| success | `200 SignedIn`: `createdAccount` → SCR-09; otherwise **the remembered section, cleared on use, or SCR-10** (AC-173) | — | — |
| empty | N/A: a form with no collection | — | — |

```text
W-07a  SCR-07 default
                 [ teleX logo 96 ]
+------------------------------------------+
|            Check your email              |   h1
|  We sent a sign-in link and a 6-digit    |
|  code to test.user+work@example.test.    |
|  Open the link, or type the code here.   |
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
|   test.user+work@example.test to sign in.|
|          [ Send a new link ]             |   primary
+------------------------------------------+

W-07d  resent
|  +------------------------------------+  |
|  | (i) We sent a new email to         |  |   alert, info-subtle
|  |     test.user+work@example.test.   |  |
|  |     Only the newest email works.   |  |
|  +------------------------------------+  |
|  ...default content...                   |
```

### SCR-08 — Confirm sign-in link

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| loading | Link opened; `previewSignInLink` reads the grant without redeeming it (AC-86) | Auth layout + `LoadState state="loading"` (`rows=2`) inside the card | — |
| default | `200 SignInLinkPreview`, still usable. Nothing happens until the click (AC-86) | h1 "Sign in to teleX", "Confirm to sign in on this device.", `Button` primary block "Continue as `<address>`", `small` "Nothing happens until you continue." | W-08a |
| submitting | "Continue as" chosen; `redeemSignInLink` | Busy `Button` ("Signing in") | W-08a (busy) |
| refused-expired / refused-used / refused-void | `410` on open or on confirm (AC-35, AC-84, AC-85, AC-103) | Same layout and copy as SCR-07 `refused-*`, using the `email` from the problem response | W-07c |
| link-unusable | `410` without `email` (unknown token), or `400 validation-failed` (`linkToken` missing) | h1 "This link can't be used", `EmptyState kind="blocked"` (icon `ban`) "Open the newest email from teleX, or sign in again.", action "Back to sign in" → SCR-01 | W-08b |
| resending | "Send a new link" chosen | Busy `Button`; `201` → SCR-07 for the same address | — |
| error | `503 mail-unavailable` on resend, `403`, or no answer within 10 s | Shared failure routing (outside the shell) → SCR-93 | — |
| success | `200 SignedIn`: `createdAccount` → SCR-09; otherwise **the remembered section, cleared on use, or SCR-10**. A link confirmed in a different browser finds nothing remembered and lands on SCR-10 (AC-173) | — | — |
| empty | N/A: one record, no collection | — | — |

```text
W-08a  SCR-08 default
                 [ teleX logo 96 ]
+------------------------------------------+
|            Sign in to teleX              |   h1
|  Confirm to sign in on this device.      |
|  [ Continue as test.user+work@example.test ] |   primary, block (wraps on phone)
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
| default | Right after a redeem answered `createdAccount: true`, on a browser that can create passkeys (AC-89, AC-91, AC-173) | Auth layout, `Icon lock`, h1 "Sign in faster next time", "Create a passkey to sign in with your fingerprint, face or screen lock instead of an email.", `Button` primary "Create a passkey", `Button` ghost "Not now" | W-09a |
| unsupported | The browser can't create passkeys (AC-90) | h1 "Passkeys aren't available here", `Icon info-circle`, "This browser doesn't support passkeys. You can add one later from another device in Profile and security.", `Button` primary "Continue" → **the remembered section or SCR-10** | W-09b |
| waiting | Options → device check → `registerPasskey` in progress | Busy primary `Button` ("Waiting for your device"); "Not now" disabled | W-09a (busy) |
| failed | Device check cancelled or failed, or `400 passkey-registration-failed` (AC-105) | Tabler `alert` danger-subtle (`alert-circle`) "No passkey was created. Try again, or choose Not now."; primary becomes "Try again"; "Not now" stays | W-09c |
| no-flag | SCR-09 opened without the `createdAccount` flag (reload, direct address) | Redirects to **the remembered section or SCR-10** | — |
| success | `200 PasskeyRegistered` → **the section remembered before sign-in, cleared on use, or SCR-10 when there's none** (AC-173, sad §8 Return after sign-in). The passkey appears on SCR-64 as "Never used" (AC-89) | — | — |
| not-now | "Not now" from `default` or `failed` → **the remembered section or SCR-10**. The step never comes back on any device (AC-91) | — | — |
| error | `401` → SCR-01 or SCR-92; `5xx`, `403` or no answer within 10 s → SCR-93 | Shared failure routing (outside the shell) | — |
| empty | N/A: a single step | — | — |

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

### SCR-10 — Inbox

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| loading | N/A: `AppLayout` renders the shell only once `getMe` has answered, so the Inbox never mounts without `me`. The shell's W-S0 "starting" state covers that moment, and drawing the shell earlier would break AC-173 (nothing of the shell until a live session). `InboxPage` keeps its `LoadState` branch only as a guard (review 2026-10-03-3, R3-1) | — | — |
| default (empty) | Start screen without another destination (AC-170). `linkedAccountCount = 0` and no Inbox source exists before E11 (AC-174, AC-100) | Inside `AppShell` with Inbox current: h1 "Inbox", `EmptyState kind="first"` (icon `brand-telegram`) "Connect your Telegram account to start." + "Connect Telegram" | W-10a |
| note | "Connect Telegram" chosen (platform-skeleton §8 OQ default) | `Toast` info "Telegram linking is coming next." (dismisses itself; choosing again shows it again) | W-10a (toast) |
| with-items | N/A before E11: no feature puts items in the Inbox. The counter alone is tested through the e2e fixture source (AC-174) | — | — |
| error | Shared failure routing (inside the shell) | — | — |

```text
W-10a  SCR-10 default inside the shell (desktop; phone = W-S2 with this card full width)
+------------------+-------------------------------------------------+
| tX teleX         |  Inbox                                          |
| ▌ Inbox          |  +-------------------------------------------+  |
|   ...            |  |            (brand-telegram)               |  |   EmptyState first
|                  |  |  Connect your Telegram account to start.  |  |
|                  |  |          [ Connect Telegram ]             |  |
|                  |  +-------------------------------------------+  |
|                  |                  +-----------------------------+|
|                  |                  | (i) Telegram linking is     ||   Toast info
|                  |                  |     coming next.            ||   (after the action)
|                  |                  +-----------------------------+|
+------------------+-------------------------------------------------+

W-10b  loading (content area only)
|  Inbox                                          |
|  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒                  |   LoadState rows=3
|  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒                           |
|  ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒                      |
```

### SCR-64 — Profile and security

Platform-skeleton's Passkeys and Sign-in sessions cards and all their states are kept, inside `AppShell` with **Settings** current. E06 adds two cards **above** them: **Appearance** and **Time zone**. Passkey and session dates now use `Me.timeZone` (AC-183). The page reads `getMe`, which the shell already has, and `listTimeZones` only when the picker opens.

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | Loaded | h1 "Profile and security", `text-secondary` "Signed in as {email}", then the cards Appearance, Time zone, Passkeys, Sign-in sessions | W-64a |
| theme-default | `Me.theme` | Card "Appearance": `ThemeSwitch variant="segmented"` (Tabler `form-selectgroup`, radio, `aria-label` "Theme"): `sun` "Light", `moon` "Dark", `device-desktop` "System", with the theme applied on this device checked. `small` "System follows your device's light or dark mode." | W-64a |
| theme-applied | An option chosen (AC-179, seed flow 3) | `data-bs-theme` changes on every screen at once (≤ 200 ms, no reload). The choice is remembered on this device, the `ThemeSwitch` in the shell shows it too, and `changeMyPreferences {theme}` is sent. The radios stay enabled, and a newer choice supersedes the one in flight | W-64a |
| theme-saved | `200` | No message: the checked option already shows the saved choice | — |
| theme-save-failed | `400 unknown-theme`, `403`, or no answer (AC-182) | The theme reverts to the previous choice everywhere and the device memory reverts. Error `Toast` "Your theme wasn't saved." with action **"Try again"**, which re-applies and re-sends the failed choice. With no answer, the shell banner also shows | W-64a (toast) |
| theme-system-follows | System chosen and the device switches mode (AC-180) | Page follows live; the radios stay on System | — |
| tz-default | `Me.timeZone` set, `timeZoneIsFallback = false` | Card "Time zone": h4 "Kyiv" + `small` "Europe/Kyiv · UTC+03:00" (offset computed in the SPA with `Intl`) + `Button` secondary "Change time zone". `small` "Dates and times in teleX use this time zone on all your devices." | W-64a |
| tz-fallback-hint | `timeZoneIsFallback = true` (AC-183) | As `tz-default` showing "UTC", plus Tabler `alert` info-subtle with `Icon info-circle`: "We couldn't read your device's time zone, so teleX uses UTC." + `Button` link "Choose yours", which opens the picker | W-64b |
| tz-not-yet | `Me.timeZone = null` while `saveDetectedTimeZone` runs (AC-183, Flow 7) | `LoadState` (`rows=1`) in the Time zone card | — |
| tz-picking | "Change time zone" or "Choose yours" chosen (AC-184, Flow 8) | `NEW: TimeZonePicker` in a Tabler `modal` (`modal-dialog-centered`, max 480 px; **full screen below 768 px**, `modal-fullscreen-md-down`). Header "Choose your time zone" + close `Button` ghost `icon="x"`. Body: `form-control` with `Icon search`, label "Search by city or region", autofocus; a `listbox` (Tabler `list-group`, scrolls) of matches "Kyiv — Europe/Kyiv", with the current zone marked `check`. Footer: `Button` ghost "Cancel". Focus is trapped, Escape closes, and focus returns to the opener. No clear or empty option (AC-186) | W-64c |
| tz-list-loading | `listTimeZones` in flight | `LoadState` (`rows=3`) in the listbox | — |
| tz-no-match | Search matches nothing (AC-185) | Listbox replaced by `small` with `Icon search`: "No time zone matches “{query}”. Try a nearby city." The current zone is unchanged | W-64c |
| tz-list-failed | `listTimeZones` got no answer | Listbox replaced by `small` "The time zone list didn't load." + `Button` link "Try again". The banner explains why | — |
| tz-saving | A zone picked → the dialog closes → `changeMyPreferences {timeZone}` | "Change time zone" `Button busy` ("Saving") | — |
| tz-saved | `200` (AC-184) | The card shows the new zone, the fallback hint disappears, every date on the page re-renders, and a `Toast` info "Time zone saved." | — |
| tz-refused | `400 unknown-time-zone` or `time-zone-required` (AC-186, spec §6.1) | The current zone stays. Inline `invalid-feedback` with `Icon alert-circle` under the card: "Choose a time zone from the list." | W-64a (variant) |
| tz-save-failed | No answer (Flow 8 `else` no answer) | The current zone stays. Error `Toast` "Your time zone wasn't saved." with action **"Try again"**, which re-sends the picked zone. The shell banner shows too | — |
| passkeys-loading / -list / -empty / -unsupported, adding / add-cancelled / add-failed / added, remove-confirm / removing / removed | Unchanged from platform-skeleton SCR-64. "Created … · Last used …" dates use `Me.timeZone` | As in platform-skeleton: `LoadState`, `list-group`, `EmptyState kind="first"`, busy `Button`, `Toast` error, `ConfirmDialog` | platform-skeleton W-64a–c |
| sessions-loading / -list / -only-this, ending / ended / ending-others | Unchanged from platform-skeleton SCR-64. "Active …" dates use `Me.timeZone` | As in platform-skeleton: `LoadState`, `list-group`, `Badge neutral` "This device", busy `Button` | platform-skeleton W-64a, W-64d |
| error | Shared failure routing (inside the shell) | — | — |

```text
W-64a  SCR-64 default inside the shell, Settings current (desktop; phone: cards stack full width, 16 px gutter)
+------------------+-------------------------------------------------+
| tX teleX         |  Profile and security                           |
|   ...            |  Signed in as test.user@example.test            |
| ▌ Settings       |                                                 |
|                  |  +- Appearance ------------------------------+  |
|                  |  | Theme                                     |  |
|                  |  | [(sun) Light][(moon) Dark●][(desktop) System] |   ThemeSwitch segmented
|                  |  | System follows your device's light or     |  |
|                  |  | dark mode.                                |  |
|                  |  +-------------------------------------------+  |
|                  |  +- Time zone -------------------------------+  |
|                  |  | Kyiv                  [ Change time zone ]|  |
|                  |  | Europe/Kyiv · UTC+03:00                   |  |
|                  |  | Dates and times in teleX use this time    |  |
|                  |  | zone on all your devices.                 |  |
|                  |  | tz-refused: (!) Choose a time zone from   |  |   invalid-feedback
|                  |  |             the list.                     |  |
|                  |  +-------------------------------------------+  |
|                  |  +- Passkeys ... (platform-skeleton) --------+  |
|                  |  +- Sign-in sessions ... --------------------+  |
+------------------+-------------------------------------------------+
                          +--------------------------------------------+
  theme-save-failed:      | (!) Your theme wasn't saved. [Try again] x |   Toast error + action
                          +--------------------------------------------+

W-64b  tz-fallback-hint
|  +- Time zone ---------------------------------------------+  |
|  | UTC                               [ Change time zone ]  |  |
|  | UTC · UTC+00:00                                         |  |
|  | +-----------------------------------------------------+ |  |
|  | | (i) We couldn't read your device's time zone, so    | |  |   alert info-subtle
|  | |     teleX uses UTC. [Choose yours]                  | |  |
|  | +-----------------------------------------------------+ |  |
|  +---------------------------------------------------------+  |

W-64c  tz-picking: modal (desktop) / full-screen sheet (phone)
+------------------------------------------------+
| Choose your time zone                     (x)  |   modal header
|------------------------------------------------|
| Search by city or region                       |
| [(search) kyi_                              ]  |   form-control, autofocus
| +--------------------------------------------+ |
| | Kyiv — Europe/Kyiv                       ✓ | |   listbox; ✓ = current
| | ...                                        | |   ↑↓ Enter Escape
| +--------------------------------------------+ |
|------------------------------------------------|
|                                    [ Cancel ]  |
+------------------------------------------------+
  no-match: (search) No time zone matches "Atlantis". Try a nearby city.
```

### SCR-69 — Settings

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | Settings in the side menu, or under More on phone (AC-172) | Inside `AppShell` with Settings current: h1 "Settings", one Tabler `list-group` from a settings registry. E06 has one row: `Icon user` + h4 "Profile and security" + `small` "Passkeys, sign-in sessions, theme and time zone" + `chevron-right` → SCR-64. Later epics add rows (AC-172) | W-69 |
| loading | N/A: a static registry with no request | — | — |
| empty | N/A: Profile and security is always present | — | — |
| error | N/A: requests no data; shell failures per the shared rule | — | — |

```text
W-69  SCR-69 default (phone shown; desktop the same card inside the side-menu frame)
+--------------------------------------+
| Settings                             |
+--------------------------------------+
|  Settings                            |   h1
|  +--------------------------------+  |
|  | (user) Profile and security  > |  |   list-group row → SCR-64
|  |        Passkeys, sign-in       |  |
|  |        sessions, theme and     |  |
|  |        time zone               |  |
|  +--------------------------------+  |
+--------------------------------------+
| (inbox) (msgs) (spark) (check) (dots)|   More marked current
+--------------------------------------+
```

### SCR-92 — Session ended

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | Any request answered `401 session-ended` (ended elsewhere, idle 30 days, or started 90 days ago; AC-93, AC-94, AC-96), **including the pulse** (seed flow 1, AC-173). The pulse stops, the TanStack cache and the shell are cleared before this page shows, and nothing of the Owner's data is left on screen or in Back history. It renders in the device's last theme | Bare system layout, h1 "Session ended", `EmptyState kind="blocked"` (icon `logout`) "Your session has ended. Sign in again to continue.", action "Sign in again" → SCR-01, with the section remembered at refusal kept (AC-173) | W-9x |
| loading / error / empty | N/A: a static page that requests no data | — | — |

### SCR-93 — teleX is unavailable

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | **Inside the shell (narrowed, AC-176):** a call answered with `500` (or another `5xx` except 502/503/504) or `403`. No answer, a network error, and **every** `502`/`503`/`504` go to the Status Banner instead (user decision 2026-10-03). **Outside the shell (unchanged):** no answer within 10 s, any `5xx` (including `503 mail-unavailable`), or `403` on an auth page | Bare system layout, h1 "teleX is unavailable", `EmptyState kind="blocked"` (icon `wifi-off`) "teleX didn't answer. Check your connection, then try again.", action `Button` primary `icon="refresh"` "Retry" | W-9x |
| retrying | Retry chosen; the failed request is repeated as-is | Busy `Button` ("Retrying") | W-9x (busy) |
| retry-failed | The repeat fails again | Same as `default`, plus `small` "Still no answer." in an `aria-live="polite"` region | W-9x |
| success | The repeat is answered | Back to the originating page, showing the result | — |
| cold-open | N/A: opening teleX from scratch while it's down shows the browser's or the proxy's own error page, with no teleX page involved | — | — |
| empty | N/A: no collection | — | — |

```text
W-9x  SCR-92 / SCR-93 bare system layout (the two differ only in h1, icon, sentence, action)
                    teleX                      text wordmark, not the logo image
+------------------------------------------+
|           teleX is unavailable           |   h1   | Session ended
|                (wifi-off)                |   icon | (logout)
|   teleX didn't answer. Check your        |        | Your session has ended.
|   connection, then try again.            |        | Sign in again to continue.
|            [ (refresh) Retry ]           |        | [ Sign in again ]
|            Still no answer.              |   SCR-93 retry-failed only (aria-live)
+------------------------------------------+
```

### SCR-94 — Coming soon

One page at each unbuilt section's own address: `/overview`, `/chats`, `/assistants`, `/runs`, `/tasks`. Each owning epic (E29, E04, E09, E14, E22) replaces its entry in `sections.ts` with its real page (ADR-0001).

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| default | The section is chosen in the navigation or under More, or its link is opened (AC-171) | Inside `AppShell`, with that section current: h1 = section name, `Badge tone="neutral" icon="clock"` "Coming soon", `EmptyState kind="none"` with the section's icon, one sentence (below; accepted 2026-10-03, see §Open questions), and action "Go to Inbox" → SCR-10 | W-94 |
| loading | N/A: static content; the lazily loaded chunk uses the shell's `section-loading` | — | — |
| empty | N/A: the page *is* the stand-in; it has no collection | — | — |
| error | Shell `section-load-failed`; otherwise N/A (no request) | — | — |

Sentences (messages catalog, accepted 2026-10-03):
- **Overview:** "See your day at a glance: what assistants did, what waits for you and what it cost."
- **Chats:** "Read and answer your Telegram chats, with assistants working alongside you."
- **Assistants:** "Set up the assistants that work for you, with their rules and limits."
- **Runs:** "Follow every time an assistant worked, step by step."
- **Tasks:** "Handle the tasks and approvals assistants hand to you."

```text
W-94  SCR-94 Runs (desktop; on phone More is marked current and the card is full width)
+------------------+-------------------------------------------------+
| tX teleX         |  Runs   [(clock) Coming soon]                   |   h1 + Badge neutral
|   ...            |  +-------------------------------------------+  |
| ▌ Runs           |  |               (activity)                  |  |   EmptyState none
|   ...            |  |  Follow every time an assistant worked,   |  |
|                  |  |  step by step.                            |  |
|                  |  |            [ Go to Inbox ]                |  |
|                  |  +-------------------------------------------+  |
+------------------+-------------------------------------------------+
```

### SCR-95 — More (phone only)

| State | Trigger / condition | Components (from the inventory) | Source-ref |
|---|---|---|---|
| open | "More" tapped in the bottom bar below 768 px (AC-43, Flow 4) | Tabler `offcanvas offcanvas-bottom` (`aria-label` "More", focus moves in), title "More" + close `Button` ghost `icon="x"` `ariaLabel` "Close". `list-group`: Overview, Runs and Settings (icon + label, each ≥ 44 px tall; the current one marked like the nav). A divider, then a "Theme" row with `ThemeSwitch variant="segmented"` (icon + word, fits 360 px), then `Icon logout` "Sign out" (AC-172) | W-95 |
| closed | The close button, a tap outside, Escape, or the system back action | The sheet closes and focus returns to "More": back on the screen it was opened from | — |
| navigated | A section chosen | The sheet closes → SCR-94 or SCR-69, with More marked current in the bar | — |
| theme-applied / theme-save-failed | A theme chosen in the sheet (AC-179, AC-182) | As `ThemeSwitch` on SCR-64. The sheet stays open, so the change is visible behind it | — |
| signing-out | "Sign out" chosen | The row becomes `busy` ("Signing out") → SCR-01 | — |
| desktop | N/A: at 768 px and wider every section is in the side menu, and an open sheet closes when the width crosses 768 px | — | — |
| loading / empty / error | N/A: a static registry; Sign out and theme failures use the shared routing and the toast | — | — |

```text
W-95  SCR-95 open over SCR-10 (360)
+--------------------------------------+
| Inbox                                |
|  (page dimmed)                       |
+--------------------------------------+
| More                            (x)  |   offcanvas-bottom
|--------------------------------------|
| (layout-dashboard) Overview          |
| (activity)         Runs              |
| (settings)         Settings          |
|--------------------------------------|
| Theme                                |
| [(sun) Light][(moon) Dark●][(desk) System] |   ThemeSwitch segmented
|--------------------------------------|
| (logout)           Sign out          |
+--------------------------------------+
| (inbox) (msgs) (spark) (check) (dots)|
+--------------------------------------+
```

## New components

| Component | Why no existing primitive fits | Registered in design-system |
|---|---|---|
| `ThemeSwitch` | One theme control used in three places (SCR-64 Appearance, the desktop sidebar footer, More) with one behavior: apply at once, remember on this device, save, and revert with an error Toast + "Try again" on failure (AC-179, AC-182). Two variants: `segmented` (Tabler `form-selectgroup`, icon + word) and `menu` (a `Button` ghost showing the current theme's icon and "Theme", opening a `dropdown-menu` of `menuitemradio`). Nothing in the inventory picks one of a few options, and the apply/revert logic must not be duplicated | pending |
| `TimeZonePicker` | A searchable single-choice list over about 400 items in a dialog (full screen on phone), with keyboard navigation (↑ ↓ Enter Escape) and an explicit no-match state (AC-185). It has no empty choice (AC-186). Tabler has no combobox, a native `<select>` can't be searched by city on iOS, and `ConfirmDialog` is a confirmation, not a chooser. Built from Tabler `modal` + `form-control` + `list-group` with the ARIA combobox/listbox pattern | pending |
| `Toast` — `action` prop (extension, not a new component) | Error toasts need one "Try again" action (theme and timezone save-failed, user decision 2026-10-03). Adds `action?: { label: string; onClick: () => void }`, rendered as `Button` link before the close button. The toast stays until dismissed or acted on | pending |
| `AppShell` (C-01), port | Not new: the reference component, ported with the deltas in §Shell (real routes, phone More, no header items in E06, footer with email + ThemeSwitch + Sign out, current-section bar, 99+). Its inventory row flips from "not yet ported" to `frontend/src/shell/AppShell/` | pending |
| `StatusBanner` (C-04), port | Not new: the reference component, ported with a catalog-driven `conditions` prop (code → icon, text, action, importance), the two client conditions `offline` / `not-responding`, the "still down" text, and "+N more" expanding inline. Its inventory row flips to `frontend/src/shell/StatusBanner/` | pending |
| `Icon` names (extension) | The subset gains `inbox`, `messages`, `sparkles`, `activity`, `checklist`, `settings`, `layout-dashboard`, `dots`, `cloud-off`, `sun`, `moon`, `device-desktop`, `chevron-right`, `x` | pending |
| `PageFrame` | **Removed.** Replaced by `AppShell`; its inventory row is deleted | pending |

## Open questions

- [x] **Coming soon sentences** (SCR-94, five sections). Accepted as shipped in `frontend/src/messages.ts` (`comingSoon.sentences`); the owning epic rewrites its sentence when it replaces the page. — owner: Anton Husiev (PM), settled 2026-10-03 (review E2)

## Noted gaps

The pipeline should close these. Each has a default that `implement` follows until it's decided.

1. **No desktop header bar in E06.** The reference AppShell's header holds only the account switcher and Stop all, and neither exists yet (spec §3). Default: no header bar on desktop, and the banner sits at the top of the main column; E02/E04 or E23 add the bar with its first item. Owner: Designer, at E02's `/sdd:screens`.
2. **Theme in the shell and the picker as a dialog differ from `ux-flows.md`**, whose platform decisions say "Theme and timezone are edited in place on SCR-64. No separate page or dialog." Decided here (user, 2026-10-03): theme also in the sidebar footer and under More, and the timezone picker in a dialog. AC-179 ("on Profile and security") still holds. Done: `ux-flows.md` §Platform decisions and Flow US-73 were updated (T27).
3. **Every 502/503/504 inside the shell goes to the banner, whatever its body** (user decision 2026-10-03). `sad.md` §8 Failure routing, ADR-0004 and `contracts/openapi.yaml` `info.description` said "from the proxy". Done: their wording no longer says "from the proxy" (T34).
4. **A failed timezone save has no AC** (ux-flows ledger 3, sad §6 flags). Default: drawn like AC-182 (`tz-save-failed` with Try again, `tz-refused`). Owner: PM, at the next `clarify` pass.
5. **Three new SCR ids** (SCR-69, SCR-94, SCR-95) still need adding to `docs/docs/03-product-spec.md`. Owner: PM, due before `/sdd:ship app-shell`.
6. **Spec §8 OQs settled here** (phone bar sections, banner importance order). Done: `spec.md` §8 ticks both, dated 2026-10-03 (T27).
