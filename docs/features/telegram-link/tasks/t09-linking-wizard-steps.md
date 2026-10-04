---
id: T9
title: "Run the phone, code, resend and password steps with Telegram's refusals and waits"
layer: "app"
deps: ["T8"]
blocks: ["T10"]
acs: ["AC-02", "AC-106", "AC-107"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/Linking.kt", "backend/app/src/main/kotlin/telex/messaging/internal/attempt/", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingStepsIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "done"
---

# T9 — Run the phone, code, resend and password steps with Telegram's refusals and waits

## Place in the sequence

- **Blocked by:** T8 — Start, resume, cancel and expire the in-memory linking attempt (one per Owner) · **Blocks:** T10 — Complete an authorized attempt: link a new account, sign in again, or refuse and log out · **Wave:** 4 — extends the attempt from T8 (same files — serialized).
- **Lane:** shares `telex/messaging/Linking.kt` with T8; shares `telex/messaging/Linking.kt` with T10 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to link my Telegram account to teleX with my phone number, the code Telegram sends me and my password if I have one
> **So that** teleX can work with my chats on my behalf
>
> — `spec.md §4, US-02, verbatim` · full text: [spec.md](../spec.md)

This task carries the Owner through the sign-in steps Telegram itself asks for, with plain answers for every way a step can fail.

## Inlined context

> Flows 5–7: check the attempt is this Owner's and its Sign-in Session is live, record the step time → send to Telegram.
> Phone: not valid / no Telegram account / banned → refusal, attempt stays at the phone step; wait still running → discard the attempt and destroy its Telegram session, refusal with the retry time; code sent → code step.
> Code: wrong / expired → refusal, stays at the code step; attempts limited → discard + wait; right → password step, or the outcome of flow 1. Resend → new code, stays at the code step.
> Password: wrong → refusal with the hint if the Owner set one; attempts limited → discard + wait; right → outcome of flow 1.
> Postconditions: teleX never creates a Telegram account; the code and password went straight to Telegram and were never stored, shown back or logged.
>
> — `sad.md §6, Flows 5, 6, 7, abridged` · full text: [sad.md](../sad.md)

> Gap 1, step mismatch: […] The contract answers 409 `linking-step-mismatch` with `step`, changes nothing, and the SPA re-renders that step.
> Gap 2, Telegram unreachable during a wizard step: […] The contract answers 503 `telegram-unavailable`, and the attempt stays at its step. The timeout value is for `sequences` / `design`.
>
> — `contracts/api-sync-report.md §B 4, abridged` · full text: [api-sync-report.md](../contracts/api-sync-report.md)

> **Hard rule:** Login code and password go from the request straight to TDLib and are never stored or echoed. Telegram's own error texts are never shown raw; each maps to a code.
>
> — `sad.md §8, Secrets + Internationalisation, abridged` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — module API behind (T15): `submitLinkingPhone` (`422` `telegram-phone-invalid|-unregistered|-banned`), `resendLinkingCode`, `submitLinkingCode` (`422` `telegram-code-wrong|-expired`), `submitLinkingPassword` (`422` `telegram-password-wrong` + `passwordHint`); all: `429 telegram-wait-required` + `retryAt` (attempt ended), `409 linking-step-mismatch` + `step`, `404 linking-attempt-not-found`, `503 telegram-unavailable` (attempt kept). Code step answers carry `codeLength`; password step carries `passwordHint`.

— `contracts/openapi.yaml, operationIds submitLinkingPhone / resendLinkingCode / submitLinkingCode / submitLinkingPassword, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

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

- [ ] Step guard shared by all four: attempt exists (`linking-attempt-not-found`), its Sign-in Session is live (else discard), step matches (`linking-step-mismatch` with the current step), record the step time
- [ ] `submitPhone`: keep digits only (none → `telegram-phone-invalid`), `sendPhone` → code step with `codeLength`, or the refusal
- [ ] `resendCode`, `submitCode` (→ password step with hint, or hand `Authorized` to the completion hook T10 fills), `submitPassword` (wrong → hint in the problem)
- [ ] `WaitRequired(s)` on any step → discard the attempt (close + destroy) and refuse with `retryAt = clock.now + s`
- [ ] Step timeout below the SPA's 10 s no-answer routing → `telegram-unavailable`, step kept; the value is still open upstream (api-sync §D gap 2) — note the chosen value in the commit body
- [ ] Metrics `telex.linking.step.duration{step}` and `telex.linking.attempts{outcome=flood_wait}`; no request values in logs
- [ ] `LinkingStepsIT` (fake adapter): every branch, plus timings recorded for the p95 ≤ 3 s target

## Edge cases

| Case | Behaviour |
|---|---|
| Number without a Telegram account | `telegram-phone-unregistered` — teleX never creates an account (AC-107) |
| Same number again during Telegram's wait | Telegram answers the wait again; no code sent (AC-02) |
| Wrong password, no hint set | `telegram-password-wrong` with `passwordHint: null` (AC-106) |
| Step from a stale tab | `409 linking-step-mismatch` + current `step`, nothing changes |

## Definition of Done

- [ ] `LinkingStepsIT` passes for every branch; step timings on the fake meet p95 ≤ 3 s
- [ ] no code, password or hint reaches logs or storage
- [ ] detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
