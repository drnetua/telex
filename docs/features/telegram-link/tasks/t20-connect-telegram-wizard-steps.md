---
id: T20
title: "Build the SCR-02 wizard steps: phone, code, password with their validation and refusals"
layer: "ui"
deps: ["T19"]
blocks: ["T21"]
acs: ["AC-01", "AC-02", "AC-106", "AC-107"]
files_hint: ["frontend/src/pages/connect-telegram/", "frontend/src/app/AppRoutes.tsx"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T20 — Build the SCR-02 wizard steps: phone, code, password with their validation and refusals

## Place in the sequence

- **Blocked by:** T19 — Build LinkedAccountSummary, port StatusBanner, extend CodeInput, Icon and PageFrame · **Blocks:** T21 — Build the SCR-02 outcomes: wait countdown, refusals, attempt ended, cancel and success · **Wave:** 3 — needs the components (T19); the terminal states follow in T21 (same page — serialized).
- **Lane:** shares `frontend/src/pages/connect-telegram/` with T21; shares `frontend/src/app/AppRoutes.tsx` with T22; shares `frontend/src/app/AppRoutes.tsx` with T23 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to link my Telegram account to teleX with my phone number, the code Telegram sends me and my password if I have one
> **So that** teleX can work with my chats on my behalf
>
> — `spec.md §4, US-02, verbatim` · full text: [spec.md](../spec.md)

This task builds the part of the wizard the Owner types into, so linking feels like Telegram's own sign-in.

## Inlined context

> SCR-02 — on every load it calls `getMyLinkingAttempt` and renders the step that answers (AC-109). It never keeps a step in the browser. The card title is "Connect your Telegram" for a new link, and "Sign in again to `<displayName>`" plus the masked phone when `targetLinkedAccountId` is set. Every step shows a ghost `Button` "Cancel" at the bottom. Onboarding card layout (`page-center`, logo 96 px, `card` ≤ 420 px, 16 px gutter on phone).
>
> — `screens.md §SCR-02 intro + Shared conventions, abridged` · full text: [screens.md](../screens.md)

> | State | Trigger | Shows |
> |---|---|---|
> | loading | `getMyLinkingAttempt` in flight | `LoadState state="loading"` rows=3 |
> | phone | `step: phone` | intro copy; `form-control` tel "Phone number"; "Include the country code."; primary "Send code"; ghost "Cancel" |
> | phone-validation | empty / `400` on `phoneNumber` | `is-invalid` "Enter your phone number with the country code."; focus |
> | phone-refused | `422 telegram-phone-invalid|-unregistered|-banned` | per-code invalid-feedback; field keeps the number |
> | code | `step: code` | "Telegram sent the code to your other devices, not by SMS. …"; `CodeInput length=codeLength`; "Continue"; ghost "Send a new code"; "Cancel" |
> | code-validation | fewer digits / `400` on `code` | "Enter all `<codeLength>` digits of the code." |
> | code-wrong | `422 telegram-code-wrong` | `CodeInput invalid` + "That code is not right. Try again, or send a new code."; digits cleared, focus first |
> | code-expired | `422 telegram-code-expired` | "This code has expired. Send a new code."; "Send a new code" becomes primary |
> | resending / resent | `resendLinkingCode` / `200` | busy ghost "Sending a new code" / digits cleared + Toast info "Telegram sent a new code." |
> | password | `step: password` | 2FA copy; `type=password autocomplete=off`; hint line when set; "Continue"; "Forgot your password? It can only be reset in the Telegram app." |
> | password-validation / password-wrong | empty / `422 telegram-password-wrong` | "Enter your two-step verification password." / "That password is not right." + hint kept, reset line in body text, field cleared + focused |
> | submitting | any step submitted | busy primary ("Sending code" / "Checking the code" / "Checking the password"); fields read-only; Cancel disabled |
> | telegram-unavailable | `503 telegram-unavailable` | Toast error "Telegram didn't answer. Check your connection and try again."; step kept (password cleared) |
> | step-mismatch | `409 linking-step-mismatch` | refetch `getMyLinkingAttempt` → render `step` + Toast info "This step was already completed in another window." |
>
> — `screens.md §SCR-02 states table (step states), abridged` · full text: [screens.md](../screens.md)

> **Hard rule:** Secrets on screen: phone `type="tel"`, `autocomplete="tel"`, `inputmode="tel"`; password `type="password"`, `autocomplete="off"`. None of the typed values survive a failed step except the phone number in its own field.
>
> — `screens.md §Shared conventions, Secrets on screen, abridged` · full text: [screens.md](../screens.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Consumes `getMyLinkingAttempt`, `submitLinkingPhone`, `resendLinkingCode`, `submitLinkingCode`, `submitLinkingPassword` (via T18). `LinkingStepResult.outcome = next` re-renders; `linked` / `signed-in-again` and the `409` refusals / `429` are rendered by T21.

## Acceptance criteria

### AC-01 — happy

> **Given** a signed-in Owner with no Linked Account, whose Telegram account has two-step verification turned on
> **When** the Owner starts "Connect Telegram", types their phone number, types the code Telegram sent to their other devices and then types their password
> **Then** the account is shown as connected as soon as the sign-in completes, with its Telegram name and a masked phone number (only the country code and the last two digits visible), its chat list starts syncing with visible progress, and the Inbox no longer shows the "Connect Telegram" step; an account without two-step verification skips the password step
>
> — `spec.md §5, AC-01, verbatim` · full text: [spec.md](../spec.md)

### AC-02 — error

> **Given** an Owner in the linking wizard who has been sent a code
> **When** they type a wrong or expired code
> **Then** the wizard says the code is wrong or expired, lets them try again or ask for a new code, and when Telegram limits the attempts the wizard ends the attempt and says when the Owner can try again, counting down to that time; starting again with the same number before then shows the remaining wait instead of sending a code
>
> — `spec.md §5, AC-02, verbatim` · full text: [spec.md](../spec.md)

### AC-106 — error

> **Given** an Owner in the linking wizard at the password step
> **When** they type a wrong two-step verification password
> **Then** the wizard says the password is wrong, shows the password hint the Owner set in Telegram if there is one, lets them try again, and explains that a forgotten password can only be reset in the Telegram app
>
> — `spec.md §5, AC-106, verbatim` · full text: [spec.md](../spec.md)

### AC-107 — error

> **Given** an Owner in the linking wizard
> **When** they type a phone number that isn't a valid number, has no Telegram account, or that Telegram has banned
> **Then** the wizard blocks the step and says which one it is in plain language; for a number without a Telegram account it says to create the account in the Telegram app first, and teleX never creates a Telegram account itself
>
> — `spec.md §5, AC-107, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Route `/connect-telegram` in `app/AppRoutes.tsx` (signed-in layout); `pages/connect-telegram/ConnectTelegramPage.tsx` loads the attempt and renders the step
- [ ] `PhoneStep`, `CodeStep`, `PasswordStep` with the states above; copy from `messages.linking` only
- [ ] Error mapping per code on each step; `step-mismatch` and `telegram-unavailable` handling
- [ ] Vitest per state (mocked API), including focus management and cleared secrets

## Edge cases

| Case | Behaviour |
|---|---|
| Reload mid-wizard | Lands on the server's step, not the phone step (AC-109) |
| Account without 2FA | No password step — the code step answers the outcome directly (AC-01) |
| `passwordHint` null | No hint line |

## Definition of Done

- [ ] Vitest for every state listed passes
- [ ] `pnpm run check` clean; layout verified at 360 px and 1280 px (e2e in T25)
- [ ] every Hard Rule inlined above still holds
