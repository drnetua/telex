---
id: T16
title: "Build Create a passkey (SCR-09) and \"Sign in with a passkey\" on SCR-01"
layer: "ui"
deps: ["T15"]
blocks: ["T18", "T20"]
acs: ["AC-89", "AC-90", "AC-91", "AC-105"]
files_hint: ["frontend/src/pages/create-passkey/", "frontend/src/pages/sign-in/", "frontend/src/api/webauthn.ts", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T16 — Build Create a passkey (SCR-09) and "Sign in with a passkey" on SCR-01

## Place in the sequence

- **Blocked by:** T15 — Build Confirm sign-in link (SCR-08) and the landing-after-sign-in rule · **Blocks:** T18 — Build Profile and security (SCR-64): passkeys card and sign-in sessions card, T20 — Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator · **Wave:** 4 — edits SCR-01 from T14 and uses the landing helper from T15.
- **Lane:** shares `frontend/src/pages/sign-in/` with T14 (serialized after T14/T15); `api/webauthn.ts` is reused by T18.

## Why (user story)

> **As an** Owner
> **I want** to create a Passkey after my first sign-in (or skip it) and use it to sign in later
> **So that** signing back in takes one touch instead of a trip to my mailbox
>
> — `spec.md §4, US-45, verbatim` · full text: [spec.md](../spec.md)

This task offers a passkey right after the account is created and lets the Owner sign back in with it from the sign-in page.

## Inlined context

**SCR-09 states** (reached only right after a redeem answered `createdAccount: true`):
- `default` — auth layout, `Icon lock` (24 px), h1 "Sign in faster next time", "Create a passkey to sign in with your fingerprint, face or screen lock instead of an email.", `Button` primary "Create a passkey", `Button` ghost "Not now".
- `unsupported` (AC-90) — h1 "Passkeys aren't available here", `Icon info-circle`, "This browser doesn't support passkeys. You can add one later from another device in Profile and security.", `Button` primary "Continue" → SCR-10.
- `waiting` — options → device check → register: busy primary ("Waiting for your device"); "Not now" disabled.
- `failed` (AC-105) — cancelled/failed check or `400 passkey-registration-failed`: `alert` danger-subtle (`alert-circle`) "No passkey was created. Try again, or choose Not now."; primary label "Try again"; "Not now" stays.
- `no-flag` — opened without the flag (reload, direct address) → redirect to SCR-10. `success` — `200` → SCR-10. `error` → shared routing.
"Not now" from `default` or `failed` goes to SCR-10, and the step never comes back on any device (AC-91).

**SCR-01 passkey states:** `passkey-waiting` — busy secondary `Button` `icon="lock"` "Sign in with a passkey" → ("Waiting for your device"); `passkey-failed` — `401 passkey-rejected`: `alert` danger-subtle above the form "That passkey didn't work. Sign in with your email instead."; a cancelled check or no passkey on the device → back to `default` silently; success → landing helper (T15) with `createdAccount: false`.

— `screens.md §SCR-09 + §SCR-01, abridged` · full text: [screens.md](../screens.md) · wireframes W-09a–c, W-01c

> Flow US-45 passkey step: check whether this browser can create passkeys → *cannot* → SCR-09 explains (AC-90) → *capable* → Create a passkey → ask for registration options → create a credential on the authenticator → *cancelled, or the device check fails* → no passkey was created, Try again or Not now (AC-105) → *confirmed* → register → *verification fails* → same → *verified* → SCR-10, the passkey shows as Never used on SCR-64 (AC-89).
>
> — `sad.md §6, Flow US-45 passkey step, abridged` · full text: [sad.md](../sad.md)

**Capability check:** `window.PublicKeyCredential` present and `PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()` resolves (or the JSON helpers `parseCreationOptionsFromJSON` exist); fall back to manual Base64URL conversion when the JSON helpers are missing (Safari). **Reuse:** T14 `AuthLayout`, T13 `Button`/`Icon`, Tabler `alert`.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

- `passkeyRegistrationOptions` `POST /webauthn/register/options` → `200` creation options (`challenge`, `user.id` Base64URL) · `401`.
- `registerPasskey` `POST /webauthn/register` `{publicKey: {credential, label}}` (label ignored) → `200 {success: true}` · `400 passkey-registration-failed`.
- `passkeyAuthenticationOptions` `POST /webauthn/authenticate/options` → `200` request options, `allowCredentials: []`.
- `signInWithPasskey` `POST /login/webauthn` (assertion JSON) + `X-Telex-Time-Zone` → `200 {createdAccount: false}` · `401 passkey-rejected`.

— `contracts/openapi.yaml, tag passkeys, ceremony operations, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-89 — happy

> **Given** an Owner on a passkey-capable browser who has just signed in for the first time
> **When** they accept "Create a passkey" and confirm with their device
> **Then** the passkey is listed in Profile and security, named automatically after its browser and device (for example "Safari on iPhone"), with its creation date and its last-used date ("Never used" until first use), and their next sign-in on that device completes with "Sign in with a passkey" on the sign-in page and the device check alone, without typing an email address
>
> — `spec.md §5, AC-89, verbatim` · full text: [spec.md](../spec.md)

### AC-90 — error

> **Given** an Owner whose browser can't create passkeys
> **When** the passkey step appears after their first sign-in
> **Then** it explains that this browser doesn't support passkeys and that they can add one later from another device, and it continues to the Inbox; sign-in by email keeps working
>
> — `spec.md §5, AC-90, verbatim` · full text: [spec.md](../spec.md)

### AC-91 — happy

> **Given** an Owner on the passkey step after their first sign-in
> **When** they choose "Not now"
> **Then** they land on the Inbox, and Profile and security shows "No passkeys yet" with an "Add a passkey" action; the passkey step only follows the sign-in that creates the account and is not offered again on later sign-ins, on any device
>
> — `spec.md §5, AC-91, verbatim` · full text: [spec.md](../spec.md)

### AC-105 — error

> **Given** an Owner on a passkey-capable browser on the passkey step after their first sign-in
> **When** they cancel the device check, or creating the passkey fails
> **Then** they stay on the passkey step, see that no passkey was created, and can try again or choose "Not now"; no passkey is added to the list
>
> — `spec.md §5, AC-105, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `frontend/src/api/webauthn.ts` — `canCreatePasskey()`, `createPasskey()`, `signInWithPasskey()` (options → `navigator.credentials` → POST), distinguishing "cancelled" (`NotAllowedError`, `AbortError`) from server refusals.
- [ ] `frontend/src/pages/create-passkey/` — SCR-09; reads `createdAccount` from router state only.
- [ ] `frontend/src/pages/sign-in/` — add the passkey button + its two states.
- [ ] Copy in `messages.ts`; Vitest with mocked `navigator.credentials`.

## Edge cases

| Case | Behaviour |
|---|---|
| Browser without `PublicKeyCredential` | SCR-09 `unsupported`; "Continue" → SCR-10 (AC-90) |
| User cancels the device prompt on SCR-09 | `failed` with "Try again" (AC-105) |
| User cancels on SCR-01 | silently back to `default` |
| Removed passkey used on SCR-01 | `passkey-failed`; email form still usable (AC-92) |
| SCR-09 reloaded | `no-flag` → SCR-10; never offered again (AC-91) |

## Definition of Done

- [ ] Vitest covers every SCR-09 state and both SCR-01 passkey states.
- [ ] Works at 360 px and 1280 px; tokens only; copy in `messages.ts`.
- [ ] `pnpm run check` clean.
- [ ] every Hard Rule inlined above still holds.
