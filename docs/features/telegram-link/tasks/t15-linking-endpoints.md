---
id: T15
title: "Expose the linking-attempt endpoints with the contract's problem codes, validation and Retry-After"
layer: "ports"
deps: ["T10"]
blocks: ["T25"]
acs: ["AC-02", "AC-106", "AC-107", "AC-109", "AC-119"]
files_hint: ["backend/app/src/main/kotlin/telex/web/api/LinkingController.kt", "backend/app/src/main/kotlin/telex/web/ProblemHandler.kt", "backend/app/src/integrationTest/kotlin/telex/web/LinkingApiIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "done"
---

# T15 — Expose the linking-attempt endpoints with the contract's problem codes, validation and Retry-After

## Place in the sequence

- **Blocked by:** T10 — Complete an authorized attempt: link a new account, sign in again, or refuse and log out · **Blocks:** T25 — Add Playwright end-to-end tests of linking, live state and unlink at 360 px and 1280 px with axe · **Wave:** 6 — needs the full attempt (T8 → T9 → T10).
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to link my Telegram account to teleX with my phone number, the code Telegram sends me and my password if I have one
> **So that** teleX can work with my chats on my behalf
>
> — `spec.md §4, US-02, verbatim` · full text: [spec.md](../spec.md)

This task opens the wizard to the browser through one singleton attempt resource, so every tab and device sees the same step.

## Inlined context

> One linking attempt per Owner as a singleton resource (`/api/v1/linking-attempt`), not `/linking-attempts/{id}`. sad §8 allows at most one per Owner, so the caller never needs an id, and no attempt id ever reaches the browser.
> Status mapping: 503 for "not set up" […]; 409 for post-authorization refusals and the limit; 422 for Telegram refusing a typed value; 429 + `Retry-After` for Telegram's wait; 404 for "no attempt" and for anything outside the caller's accounts.
>
> — `contracts/api-sync-report.md §C 2–3, abridged` · full text: [api-sync-report.md](../contracts/api-sync-report.md)

> **Hard rule:** The phone number, login code and password go from the request straight to Telegram and are never stored, echoed or logged. Responses carry only the masked phone.
>
> — `contracts/openapi.yaml info.description, verbatim` · full text: [openapi.yaml](../contracts/openapi.yaml)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

| operationId | Path | Success | Errors |
|---|---|---|---|
| `getMyLinkingAttempt` | `GET /api/v1/linking-attempt` | `200 LinkingAttempt {step, origin, targetLinkedAccountId, codeLength, passwordHint}` | `401`, `404 linking-attempt-not-found` |
| `startMyLinkingAttempt` | `POST` same (`{origin, targetLinkedAccountId?}`) | `200` resumed / `201` new | `400`, `401`, `403`, `404 not-found`, `409 linked-account-limit-reached`(+`limit`) / `telegram-account-already-linked`, `503 telegram-linking-not-set-up` |
| `cancelMyLinkingAttempt` | `DELETE` same | `204` | `401`, `403` |
| `submitLinkingPhone` | `POST …/phone` `{phoneNumber 1..32}` | `200 LinkingAttempt` | `422 telegram-phone-*`, `429`, `409 linking-step-mismatch`, `404`, `503 telegram-unavailable` |
| `resendLinkingCode` | `POST …/code/resend` | `200` | `429`, `409`, `404`, `503` |
| `submitLinkingCode` | `POST …/code` `{code ^[0-9]{1,16}$}` | `200 LinkingStepResult` | `422 telegram-code-wrong|-expired`, `409` refusals/mismatch, `429`, `404`, `503` |
| `submitLinkingPassword` | `POST …/password` `{password 1..1024}` | `200 LinkingStepResult` | `422 telegram-password-wrong`(+`passwordHint`), `409`, `429`, `404`, `503` |

`429 telegram-wait-required` carries `retryAt` + header `Retry-After` (seconds). Every POST/DELETE needs `X-XSRF-TOKEN`.

— `contracts/openapi.yaml, tag linking, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

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

### AC-109 — domain invariant

> **Given** an Owner who started the linking wizard
> **When** they cancel it, or take no step in it for 15 minutes
> **Then** the attempt is discarded, no teleX device from it remains in the account's active sessions in Telegram, and starting again begins with the phone number; until then an Owner has at most one open attempt, and reloading the page or opening the wizard in another tab or device continues it at the step where it stopped
>
> — `spec.md §5, AC-109, verbatim` · full text: [spec.md](../spec.md)

### AC-119 — error

> **Given** an installation whose Operator hasn't given it Telegram app credentials
> **When** an Owner chooses "Connect Telegram" or "Add account"
> **Then** teleX says that Telegram linking isn't set up on this installation yet and that the Operator has to finish the setup, and doesn't start the wizard
>
> — `spec.md §5, AC-119, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `web/api/LinkingController` for the seven operations, delegating to `messaging.Linking` with the caller's `OwnerId` + current `SignInSessionId`
- [ ] Problem rendering for the 15 new codes and their extensions (`retryAt`, `passwordHint`, `limit`, `step`) via `DomainProblem` / `ProblemHandler`; `Retry-After` header on 429
- [ ] Bean validation → `400 validation-failed` with `errors[]`; make sure request bodies are never logged
- [ ] `LinkingApiIT` (fake adapter, `ContractValidator`): every row of the table above, resume from a second session of the same Owner (AC-109), not-set-up with no credentials (AC-119)

## Edge cases

| Case | Behaviour |
|---|---|
| Second tab POSTs start | `200` with the open attempt, body ignored (AC-109) |
| Code with letters | `400 validation-failed` on `code` |
| Wait on the phone step | `429` + `retryAt` + `Retry-After`; next GET → `404 linking-attempt-not-found` (AC-02) |

## Definition of Done

- [ ] `LinkingApiIT` passes; every documented response validates against `openapi.yaml`
- [ ] a captured-log assertion shows no phone, code or password
- [ ] detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
