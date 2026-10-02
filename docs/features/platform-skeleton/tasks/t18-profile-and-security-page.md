---
id: T18
title: "Build Profile and security (SCR-64): passkeys card and sign-in sessions card"
layer: "ui"
deps: ["T16", "T17"]
blocks: ["T20"]
acs: ["AC-89", "AC-91", "AC-92", "AC-93", "AC-94", "AC-97"]
files_hint: ["frontend/src/pages/profile-security/", "frontend/src/components/ConfirmDialog/", "frontend/src/components/Badge/", "frontend/src/api/account.ts", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T18 — Build Profile and security (SCR-64): passkeys card and sign-in sessions card

## Place in the sequence

- **Blocked by:** T16 — Build Create a passkey (SCR-09) and "Sign in with a passkey" on SCR-01, T17 — Build the signed-in PageFrame with Sign out and the empty Inbox (SCR-10) · **Blocks:** T20 — Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator · **Wave:** 5 — needs `PageFrame` (T17) and the WebAuthn helper (T16).
- **Lane:** shares `frontend/src/api/account.ts` with T17 (serialized after it).

## Why (user story)

> **As an** Owner
> **I want** to see my active Sign-in Sessions and Passkeys, end any session, and sign out
> **So that** a lost device or a stolen session stops working when I say so
>
> — `spec.md §4, US-46, verbatim` · full text: [spec.md](../spec.md)

This task gives the Owner one page to see and control their passkeys and every place they are signed in.

## Inlined context

The page loads `GET /api/v1/me`, `GET /api/v1/passkeys` and `GET /api/v1/sessions`. The two cards load and fail independently. Records come only from the Owner's own data (AC-97). The sessions card has the anchor `#sessions`, which the link in the "New sign-in to teleX" email targets (AC-98). A signed-out visitor goes to SCR-01 and returns here afterwards (AC-101).

**States:**
- `default` — `PageFrame`, h1 "Profile and security", text-secondary "Signed in as `<Me.email>`", two Tabler `card`s with `list-group`s.
- `passkeys-loading` (`LoadState`, `rows=2`) · `passkeys-list` — row: h4 label, `small` "Created 2 Oct 2026 · Last used 12 min ago" or "Never used" (AC-89), `Button` ghost `icon="trash"` "Remove"; header `Button` secondary `icon="plus"` "Add a passkey" · `passkeys-empty` — `EmptyState kind="first"` (icon `lock`) "No passkeys yet.", action "Add a passkey", header button hidden (AC-91) · `passkeys-unsupported` — every "Add a passkey" replaced by `small` + `Icon info-circle` "This browser doesn't support passkeys. Add one from another device."
- `adding` (busy "Waiting for your device") · `add-cancelled` (back, no message) · `add-failed` (`Toast` error "No passkey was created. Try again.", stays until dismissed) · `added` (refetch; new row "Never used").
- `remove-confirm` — `ConfirmDialog variant="default"`: "Remove this passkey?"; "“Safari on iPhone” will no longer sign you in. Sessions already open stay open. If you lost this device, also end its session in Sign-in sessions."; confirm "Remove passkey" (`danger`); cancel "Keep passkey" · `removing` (busy "Removing") · `removed` — `204` or `404`: dialog closes, list refetches, nothing else shown.
- `sessions-loading` · `sessions-list` — row: h4 `userAgentLabel`, `small` "{Phone | Tablet | Computer | Unknown device} · Active 12 min ago" (relative under a day, a date after); current row `Badge tone="neutral" icon="check"` "This device", no End button; others `Button` ghost "End session"; footer `Button` secondary "Sign out of all other sessions" · `sessions-only-this` — footer hidden · `ending` (row busy "Ending") · `ended` — `204` or `404`: refetch · `ending-others` (busy "Signing out other sessions" → `sessions-only-this`).
- `error` — shared failure routing.

— `screens.md §SCR-64, abridged` · full text: [screens.md](../screens.md) · wireframes W-64a–d

> **Confirmations stay in place on SCR-64.** Removing a passkey asks for confirmation on SCR-64 itself (AC-92). Ending one session, signing out of all other sessions and signing out act immediately, because the spec asks for no confirmation.
>
> — `ux-flows.md §Platform decisions, verbatim` · full text: [ux-flows.md](../ux-flows.md)

**Reuse:** T17 `PageFrame`, `Toast`; T13 `Button`, `EmptyState`, `Icon`; `LoadState`; T16 `api/webauthn.ts` (`canCreatePasskey`, `createPasskey`); port `ConfirmDialog` and `Badge` from `docs/docs/design-system/components/`.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

- `listMyPasskeys` `GET /api/v1/passkeys` → `{items: [{id, label, createdAt, lastUsedAt|null}]}` · `removeMyPasskey` `DELETE /api/v1/passkeys/{passkeyId}` → `204` / `404 not-found`.
- `listMySessions` `GET /api/v1/sessions` → `{items: [{id, userAgentLabel, deviceType, startedAt, lastActivityAt, current}]}` · `endMySession` `DELETE /api/v1/sessions/{sessionId}` → `204` / `404` · `endMyOtherSessions` `POST /api/v1/sessions/end-others` → `204`.
- Registration ceremony as in T16. Refetches carry `X-Telex-Background: 1` (T13).

— `contracts/openapi.yaml, operationIds listMyPasskeys / removeMyPasskey / listMySessions / endMySession / endMyOtherSessions, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-89 — happy

> **Given** an Owner on a passkey-capable browser who has just signed in for the first time
> **When** they accept "Create a passkey" and confirm with their device
> **Then** the passkey is listed in Profile and security, named automatically after its browser and device (for example "Safari on iPhone"), with its creation date and its last-used date ("Never used" until first use), and their next sign-in on that device completes with "Sign in with a passkey" on the sign-in page and the device check alone, without typing an email address
>
> — `spec.md §5, AC-89, verbatim` · full text: [spec.md](../spec.md)

### AC-91 — happy

> **Given** an Owner on the passkey step after their first sign-in
> **When** they choose "Not now"
> **Then** they land on the Inbox, and Profile and security shows "No passkeys yet" with an "Add a passkey" action; the passkey step only follows the sign-in that creates the account and is not offered again on later sign-ins, on any device
>
> — `spec.md §5, AC-91, verbatim` · full text: [spec.md](../spec.md)

### AC-92 — happy

> **Given** an Owner with one or more Passkeys, including their only one
> **When** they remove a passkey in Profile and security and confirm
> **Then** the passkey disappears from the list and can no longer sign them in, and sign-in by email keeps working; Sign-in Sessions already open on any device stay open, and the confirm step reminds the Owner to end a lost device's session in the sessions list (AC-93)
>
> — `spec.md §5, AC-92, verbatim` · full text: [spec.md](../spec.md)

### AC-93 — happy

> **Given** an Owner signed in on a laptop and a phone
> **When** they open Profile and security on the laptop and end the phone's session
> **Then** the list shows each session's browser, device type and last activity, with the current one marked "This device", and the phone's next action lands on the "Session ended" page
>
> — `spec.md §5, AC-93, verbatim` · full text: [spec.md](../spec.md)

### AC-94 — happy

> **Given** an Owner signed in on several devices
> **When** they choose "Sign out of all other sessions"
> **Then** every session except the current one ends, and the list shows only "This device"
>
> — `spec.md §5, AC-94, verbatim` · full text: [spec.md](../spec.md)

### AC-97 — authorization

> **Given** two Owners on the same installation
> **When** one Owner tries to see, end or remove the other Owner's Sign-in Session or Passkey
> **Then** nothing changes, and it looks as if that session or passkey doesn't exist; each Owner only ever sees their own
>
> — `spec.md §5, AC-97, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Queries/mutations in `frontend/src/api/account.ts`.
- [ ] `frontend/src/pages/profile-security/` — page + `PasskeysCard` + `SessionsCard` (each with its own loading/error).
- [ ] Port `ConfirmDialog`, `Badge` into `frontend/src/components/`.
- [ ] `#sessions` anchor scrolled into view on load.
- [ ] Copy in `messages.ts` (relative-time strings included); Vitest per state.

## Edge cases

| Case | Behaviour |
|---|---|
| Remove the only passkey | allowed; card switches to `passkeys-empty` (AC-92) |
| `404` on remove / end session | treated as done: refetch, no message (AC-97) |
| Sessions request fails, passkeys succeed | passkeys card renders; sessions failure follows shared routing |
| Phone width | cards stack, row actions drop below the text; dialog as full-width sheet |
| `lastUsedAt` null | "Never used" |

## Definition of Done

- [ ] Vitest covers every SCR-64 state listed above.
- [ ] Works at 360 px and 1280 px; status never by color alone ("This device" badge has icon + words); tokens only.
- [ ] `pnpm run check` clean.
- [ ] every Hard Rule inlined above still holds.
