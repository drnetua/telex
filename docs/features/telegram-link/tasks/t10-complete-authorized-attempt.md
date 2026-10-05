---
id: T10
title: "Complete an authorized attempt: link a new account, sign in again, or refuse and log out"
layer: "app"
deps: ["T5", "T9"]
blocks: ["T15", "T24"]
acs: ["AC-01", "AC-04", "AC-108", "AC-115", "AC-117"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/Linking.kt", "backend/app/src/main/kotlin/telex/messaging/internal/attempt/", "backend/app/src/main/kotlin/telex/messaging/internal/account/", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingCompletionIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "done"
---

# T10 — Complete an authorized attempt: link a new account, sign in again, or refuse and log out

## Place in the sequence

- **Blocked by:** T5 — Manage Telegram session directories: per-session dir, destroy with one-minute retry, startup orphan sweep, T9 — Run the phone, code, resend and password steps with Telegram's refusals and waits · **Blocks:** T15 — Expose the linking-attempt endpoints with the contract's problem codes, validation and Retry-After, T24 — Prove nothing is left behind or leaked: unlink dump, restart delivery, registry and log scans · **Wave:** 5 — needs the steps (T9) that produce `Authorized`, and directory destroy (T5).
- **Lane:** shares `telex/messaging/internal/account/` with T7; shares `telex/messaging/Linking.kt` with T8; shares `telex/messaging/Linking.kt` with T9; shares `telex/messaging/internal/account/` with T11 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to link my Telegram account to teleX with my phone number, the code Telegram sends me and my password if I have one
> **So that** teleX can work with my chats on my behalf
>
> — `spec.md §4, US-02, verbatim` · full text: [spec.md](../spec.md)

This task is the moment an Owner's Telegram account becomes a Linked Account, or is refused without leaving a teleX device behind.

## Inlined context

> Tg-->>Msg: authorized, user U, name, phone
> alt U is another Owner's account, or already this Owner's and connected, or the limit is now full → log out and destroy the session; refusal
> else U is this Owner's account in Session lost → seal the new TDLib key for that Linked Account; swap in the new Telegram session under the same Linked Account, Connected, no limit check; destroy the old session directory; signed in again
> else new account within the limit → seal TDLib key for the new Linked Account; insert Linked Account Connected with masked phone, record AccountLinked; linked, return to where the attempt started
>
> — `sad.md §6, Critical flow 1, abridged` · full text: [sad.md](../sad.md)

> Flow 10 (Sign in again targets A): same Telegram user as A, matched by Telegram identity, not by phone → put the new Telegram session and sealed key on A, set Connected, destroy the old session directory, record LinkedAccountStateChanged / a Telegram user that is another Owner's Linked Account → log out and destroy the new session, refusal account owned by another Owner / any other Telegram user → log out and destroy, refusal account mismatch; A stays Session lost.
>
> — `sad.md §6, Flow 10, abridged` · full text: [sad.md](../sad.md)

> Once Telegram authorizes an attempt, it either becomes a Linked Account or is logged out in the same call, so no teleX device is left behind (AC-109). […] The final count runs under a transaction-scoped Postgres advisory lock keyed by the `OwnerId`. Two Owners finishing at the same moment are settled by the index: the loser's session is logged out.
> `AccountLinked` is not published for "Sign in again" — that publishes `LinkedAccountStateChanged` instead.
>
> — `sad.md §8 Linking attempt + One Owner + Account limit; events.md, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** The sealed TDLib key uses AAD = the `LinkedAccountId`; the raw key exists only in memory. The full phone number is never stored — only country code + last two digits.
>
> — `adr/0003 §Decision outcome + sad.md §8 Personal data minimisation, abridged` · full text: [0003-seal-each-tdlib-database-key-with-a-stored-per-owner-key-under-an-installation-master-key.md](../adr/0003-seal-each-tdlib-database-key-with-a-stored-per-owner-key-under-an-installation-master-key.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Column | Change |
|---|---|
| `linked_account` (new row): `id` (UUIDv7), `owner_id`, `telegram_user_id`, `telegram_session_id`, `tdlib_key_sealed` (60 B), `display_name`, `phone_country_code`, `phone_last_digits`, `state='connected'`, `chats_total=NULL`, `chat_sync_completed_at=NULL`, `created_at` (Clock) | inserted (new link) |
| `linked_account.telegram_session_id`, `tdlib_key_sealed`, `state='connected'`, `display_name`, `chat_sync_completed_at=NULL` | updated (Sign in again) |
| `event_publication` | `AccountLinked` / `LinkedAccountStateChanged` (ids + state only) |

— `data-model.md §linked_account, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — module API behind (T15): `LinkingStepResult` `{outcome: linked|signed-in-again, linkedAccountId, origin}`; refusals `409` `telegram-account-owned-by-another-owner`, `telegram-account-already-linked`, `linked-account-limit-reached` (+ `limit`), `telegram-account-mismatch` — after each the attempt is gone.

— `contracts/openapi.yaml, responses LinkingStepAccepted / LinkingRefusedOrStepMismatch, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-01 — happy

> **Given** a signed-in Owner with no Linked Account, whose Telegram account has two-step verification turned on
> **When** the Owner starts "Connect Telegram", types their phone number, types the code Telegram sent to their other devices and then types their password
> **Then** the account is shown as connected as soon as the sign-in completes, with its Telegram name and a masked phone number (only the country code and the last two digits visible), its chat list starts syncing with visible progress, and the Inbox no longer shows the "Connect Telegram" step; an account without two-step verification skips the password step
>
> — `spec.md §5, AC-01, verbatim` · full text: [spec.md](../spec.md)

### AC-04 — domain invariant

> **Given** a Telegram account that is already a Linked Account of another Owner
> **When** an Owner completes the linking wizard for that same Telegram account
> **Then** teleX blocks the link with the rule "one Telegram account belongs to one Owner", ends the sign-in it just made so no teleX device for the second Owner remains in the account's active sessions in Telegram, and leaves the other Owner's Linked Account untouched
>
> — `spec.md §5, AC-04, verbatim` · full text: [spec.md](../spec.md)

### AC-108 — domain invariant

> **Given** an Owner whose Telegram account is already one of their Linked Accounts
> **When** they go through the linking wizard for the same Telegram account again
> **Then** teleX doesn't create a second Linked Account: if that account is connected, teleX ends the sign-in it just made, so no extra teleX device remains in the account's active sessions in Telegram, and tells them the account is already linked; if that account has lost its session, the wizard counts as "Sign in again" and brings back the same Linked Account (AC-117). A Telegram account is recognized by its identity in Telegram, not by its phone number, so a changed phone number still matches
>
> — `spec.md §5, AC-108, verbatim` · full text: [spec.md](../spec.md)

### AC-115 — domain invariant

> **Given** an Owner who already has as many Linked Accounts as the installation's limit allows
> **When** they try to add another account, or finish a wizard after another account has taken the last place
> **Then** teleX doesn't start the wizard, or ends the sign-in it just made so no teleX device remains in Telegram, and tells them the limit and that they can unlink an account to free a place; every Linked Account counts toward the limit, including one that has lost its session, signing in again to a lost-session account takes no new place, and lowering the limit never unlinks existing accounts
>
> — `spec.md §5, AC-115, verbatim` · full text: [spec.md](../spec.md)

### AC-117 — error

> **Given** an Owner with a connected Linked Account
> **When** the teleX session is ended from the Telegram app or by Telegram itself
> **Then** within 5 minutes the account shows "Session lost" with a "Sign in again" action; signing in again with the same Telegram account brings back the same Linked Account with everything attached to it, while signing in with a different Telegram account at that point is refused, that sign-in is ended so no teleX device remains in Telegram, and the Owner is told to link it as a new account (or, if it belongs to another Owner, sees the rule from AC-04)
>
> — `spec.md §5, AC-117, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Completion handler called by code/password `Authorized`: one transaction, `pg_advisory_xact_lock` on the Owner, T7's decision, then insert / swap / refuse
- [ ] New link: new `LinkedAccountId`, `OwnerKeys.seal(owner, key, aad = id)`, insert Connected, publish `AccountLinked`
- [ ] Sign in again: seal for the target id, swap session id + sealed key, Connected, reset `chat_sync_completed_at`, publish `LinkedAccountStateChanged`; after commit destroy the old session directory
- [ ] Refusal or unique-index race on `telegram_user_id`: `logOut` + `destroy` the new session, map to the refusal code
- [ ] Discard the attempt in every outcome; metric `telex.linking.attempts{outcome=linked|refused_*}`
- [ ] `LinkingCompletionIT` (fake adapter): 2FA and no-2FA new links, every refusal (fake shows no session left), Sign in again with a changed phone still matching by Telegram identity

## Edge cases

| Case | Behaviour |
|---|---|
| Limit lowered or filled during the attempt | `linked-account-limit-reached` at the end, session logged out (AC-115) |
| Two Owners finish with the same Telegram account at once | Index settles it; the loser gets the AC-04 refusal and is logged out |
| Changed phone, same Telegram account | Matches by Telegram identity (AC-108) |

## Definition of Done

- [ ] `LinkingCompletionIT` passes for every branch; after each refusal the fake lists no teleX session
- [ ] the stored row has a 60-byte sealed key and a masked phone only
- [ ] detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
