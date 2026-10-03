---
status: Draft
owner: "Anton Husiev (Backend Lead)"
updated_at: "2026-10-03"
feature_size: M
---

# API sync report — telegram-link

**Inputs read:**
- `data-model.md`: present, so the default path applies (`linked_account`, `channel`; `owner_key` is internal).
- `sad.md`: `target_surfaces: [backend-service, web-frontend]`, which gives OpenAPI plus `events.md`. §6 has 3 critical flows and Flows 4–13. Also read §8 Error handling, Linking attempt and Events.
- `spec.md`: §4 US-02, US-03, US-50…US-53; §5 has 22 ACs.
- `ux-flows.md`, ADR-0001…0005, and the platform-skeleton contract (the conventions precedent).
- Code: `telex/shared/Problems.kt`, `telex/web/ProblemHandler.kt`, `telex/web/api/MeController.kt`, `application.yaml` (Modulith events).

**Outputs:**
- `contracts/openapi.yaml`: OpenAPI 3.1, 10 operations, `spectral:oas` lint 0 errors / 0 warnings.
- `contracts/events.md`: 4 registry events and 2 in-process events.

**Size / route:** M / standard (from `.size` / `.route`).

## A. Field origins

| schema_path | origin | confidence |
|---|---|---|
| listMyLinkedAccounts.items[].id | data-model.md → linked_account.id (UUIDv7) | high |
| listMyLinkedAccounts.items[].displayName | data-model.md → linked_account.display_name VARCHAR(255) | high |
| listMyLinkedAccounts.items[].phone.countryCode | data-model.md → linked_account.phone_country_code, CHECK `^[0-9]{1,3}$` | high |
| listMyLinkedAccounts.items[].phone.lastDigits | data-model.md → linked_account.phone_last_digits, CHECK `^[0-9]{2}$` | high |
| listMyLinkedAccounts.items[].state | data-model.md → linked_account.state, CHECK enum (3 values) | high |
| listMyLinkedAccounts.items[].chatSync.chatsSynced | data-model.md → derived `COUNT(channel)` per account ("Not stored", user decision 2026-10-03) | high |
| listMyLinkedAccounts.items[].chatSync.chatsTotal | data-model.md → linked_account.chats_total INTEGER NULL, CHECK ≥ 0 | high |
| listMyLinkedAccounts.items[].chatSync.completedAt | data-model.md → linked_account.chat_sync_completed_at | high |
| listMyLinkedAccounts.items[].linkedAt | data-model.md → linked_account.created_at | high |
| unlinkMyLinkedAccount.linkedAccountId | data-model.md → linked_account.id | high |
| unlinkMyLinkedAccount.signOutConfirmed | sad §6 Critical flow 2 ("confirmed / not confirmed"); response-only, not stored | medium |
| LinkingAttempt.step | sad §8 Linking attempt ("the step"); in memory, never stored (data-model "Not stored") | medium |
| LinkingAttempt.origin / StartLinkingRequest.origin | sad §8 Linking attempt ("where it started (SCR-10 or SCR-60)"); ux-flows "Where the wizard returns" | medium |
| LinkingAttempt.targetLinkedAccountId / StartLinkingRequest.targetLinkedAccountId | sad §8 Linking attempt ("the target account for Sign in again") → linked_account.id | high |
| LinkingAttempt.codeLength | added by `screens` (2026-10-03): TDLib's code info at the code step (sad §6 Flow 5 "code sent"), so `CodeInput` (C-32) can size itself; in memory, never stored | medium |
| LinkingAttempt.passwordHint / Problem.passwordHint | sad §6 Flow 7 precondition ("holding the hint Telegram gave"); spec AC-106; never stored. maxLength 255 is a request-side bound (Telegram's hint limit is shorter) | medium |
| submitLinkingPhone.phoneNumber | spec AC-107 + sad §8 (only digits passed to Telegram, never stored); maxLength 32 is a request bound | medium |
| submitLinkingCode.code | spec AC-02; digits only, Telegram decides; never stored | medium |
| submitLinkingPassword.password | spec AC-106; never stored; maxLength 1024 is a request bound | medium |
| LinkingCompleted.outcome | sad §6 Critical flow 1 alt branches ("linked" / "signed in again") | high |
| LinkingCompleted.linkedAccountId | data-model.md → linked_account.id | high |
| Problem.retryAt | sad §6 Flows 5–7 ("wait required, with the seconds left") + `Clock`; not stored (sad §8 flood wait) | medium |
| Problem.limit | sad §7 `TELEX_TELEGRAM_MAX_ACCOUNTS_PER_OWNER` (config, not a column); spec AC-115 "tells them the limit" | medium |
| Problem.step | added by api for `linking-step-mismatch` (see §B 4, sequence gap) | low |
| LiveHint | ADR-0005 (`linked-accounts` hint name) | high |
| events: ownerId / linkedAccountId / state | data-model.md → linked_account.owner_id / id / state | high |
| events: TelegramChatsChanged.ChatSnapshot.* | data-model.md → channel.telegram_chat_id, type, title, folder_ids, archived, unread_count, chat_order | high |
| getMe.linkedAccountCount (platform-skeleton contract) | now backed by data-model.md → COUNT(linked_account) per owner; was `low` in E01's report | high |

Every field has an origin. The one `low` row (`Problem.step`) belongs to a sequence gap that is parked as an OQ in §D.

## B. Drift findings

1. **Endpoint ↔ data-model** *(core)*: ✓
   - `listMyLinkedAccounts` reads `linked_account` + `channel` (count).
   - `unlinkMyLinkedAccount` deletes `linked_account` and, by cascade, `channel`.
   - The linking endpoints end in an insert or update of `linked_account`. Before that they work on the in-memory attempt, which data-model records as "Not stored".
   - `openLiveUpdates` reads no table. It is driven by events on `linked_account` changes (ADR-0005).
   - `owner_key` has no endpoint on purpose: it is internal to `identity` (ADR-0003). → Accept.
2. **Error code ↔ repo error definition** *(core)*: ✓ with a note. The repo has no central code list: codes are string literals passed to `DomainProblem` / `problemDetail`, plus `ProblemHandler.VALIDATION_FAILED` / `INTERNAL_ERROR`. As in platform-skeleton, the contract's `ErrorCode` enum is the registry.
   - Reused codes (`validation-failed`, `unauthenticated`, `session-ended`, `not-found`, `forbidden`, `internal-error`) already exist in code.
   - The 13 sad §8 codes are new.
   - Two codes are added by api (§B 4): `linking-step-mismatch` and `telegram-unavailable`.
   - All 15 new codes are kebab-case DNS-1123 labels. `implement` adds one `messages.ts` entry per code.
3. **Validation ↔ constraint** *(core)*: ✓
   - `displayName` maxLength 255 = VARCHAR(255).
   - `countryCode` / `lastDigits` patterns = the CHECKs.
   - `state` enum = `linked_account_state_ck` (`connected, reconnecting, session_lost`). The DB values are reused verbatim; this is the only snake_case enum value in the API.
   - `chatsTotal` minimum 0 = CHECK. `chatsSynced` minimum 0.
   - Request-only fields have no column, so their bounds are request bounds and are marked so in §A.
4. **OpenAPI ↔ sequence** *(supporting)*: ✓ with two sequence gaps, both parked as OQs owned by `sequences` (§D):
   - **Every §6 alt branch has a response:**

     | sad §6 branch | Response |
     |---|---|
     | Flow 4: not set up | 503 `telegram-linking-not-set-up` |
     | Flow 4: open attempt | 200 resumed |
     | Flow 4: limit | 409 `linked-account-limit-reached` |
     | Flow 4: below the limit | 201 |
     | Flow 5: phone invalid / unregistered / banned | 422 `telegram-phone-invalid` / `-unregistered` / `-banned` |
     | Flows 5–7: wait | 429 `telegram-wait-required` + `retryAt` |
     | Flow 6: code wrong / expired | 422 `telegram-code-wrong` / `-expired` |
     | Flow 6: new code | `resendLinkingCode` |
     | Flow 7: password wrong | 422 `telegram-password-wrong` + `passwordHint` |
     | Critical flow 1: other Owner / already linked / limit at the end | 409 `telegram-account-owned-by-another-owner` / `telegram-account-already-linked` / `linked-account-limit-reached` |
     | Critical flow 1: Session lost | 200 `signed-in-again` |
     | Critical flow 1: new | 200 `linked` |
     | Flow 8: cancel | 204 |
     | Flow 8: swept / Sign-in Session ended | 404 `linking-attempt-not-found`, 401 `session-ended` |
     | Flow 10: A not the caller's | 404 `not-found` |
     | Flow 10: another Owner's | 409 `telegram-account-owned-by-another-owner` |
     | Flow 10: other user | 409 `telegram-account-mismatch` |
     | Critical flow 2: confirmed / not confirmed | 200 `signOutConfirmed` true / false |
     | Flow 13: another Owner's account | 404 `not-found` |
     | Flows 9, 11, 12 | `linked-accounts` hint on `openLiveUpdates` |

   - **sad §6 flag resolved here:** "Sign in again" on an account that is not Session lost answers 409 `telegram-account-already-linked`, as the sad suggested.
   - **Gap 1, step mismatch:** the attempt can be stepped from two tabs or devices (AC-109). A step submitted at a step the attempt has left has no §6 branch. The contract answers 409 `linking-step-mismatch` with `step`, changes nothing, and the SPA re-renders that step.
   - **Gap 2, Telegram unreachable during a wizard step:** no flow shows Telegram not answering a phone, code, resend or password step (Flows 5–7 have only Telegram answers). The contract answers 503 `telegram-unavailable`, and the attempt stays at its step. The timeout value is for `sequences` / `design`. A natural bound is the p95 3 s target plus slack; Critical flow 2 uses 10 s for sign-out.

**Flags this run:** 3 — the event-transport conflict (§C 5) and the two sequence gaps. Per the drift rules that pauses the run: the transport conflict was put to the user and resolved, and the two gaps take the prescribed Save-as-OQ route with the upstream stage as owner.

## C. Deviations and decisions

1. **Inherited from platform-skeleton** (not re-decided): session cookie + CSRF instead of BearerAuth; RFC 9457 problems instead of `{code, message, details?}`; whole lists instead of cursor pages (at most a few accounts per Owner); no `Idempotency-Key`. Retries are safe without one:
   - cancel and unlink are idempotent in effect (204 / 404);
   - start answers 200 and resumes the open attempt;
   - a repeated step answers `linking-step-mismatch`, `linking-attempt-not-found` or a Telegram refusal, so nothing happens twice.
2. **One linking attempt per Owner as a singleton resource** (`/api/v1/linking-attempt`), not `/linking-attempts/{id}`. sad §8 allows at most one per Owner, so the caller never needs an id, and no attempt id ever reaches the browser.
3. **Status mapping:**
   - 503 for "not set up", the same class as E01's `mail-unavailable`.
   - 409 for post-authorization refusals and the limit.
   - 422 for Telegram refusing a typed value.
   - 429 + `Retry-After` for Telegram's wait.
   - 404 for "no attempt" and for anything outside the caller's accounts.
4. **No `GET /linked-accounts/{id}`.** The list carries everything SCR-60, SCR-10 and the Status Banner show. Fewer read paths also means fewer surfaces for AC-03.
5. **Event transport (user decision, 2026-10-03):** the `telegram` → `messaging` events are in-process and non-durable, and only `messaging`'s teleX-id events use the Modulith registry. Reason: completed publications stay in `event_publication` (default completion mode), and chat snapshots there would survive an unlink (spec §6). **This deviates from sad §8 Events**, which routes all six events through the JDBC registry. Patch sad §8 Events and the §5 note "Publishes session state and chat events" when `implement` touches the SAD, or in a `/sdd:design` reconcile.
6. **`getMe.linkedAccountCount` (E01 contract)** becomes real in E02. `identity.OwnerProfiles.me` can't count Linked Accounts: `messaging` → `identity` (OwnerKeys) already exists, so `identity` → `messaging` would be a cycle. `MeController` (in `web`) should take the count from `messaging.LinkedAccounts` instead, and the field leaves `OwnerProfiles`. A code task for `tasks`; the contract shape is unchanged.

## D. Open questions raised by this run

- [ ] **Step mismatch across tabs** (§B 4, gap 1): add the branch "the attempt is at another step → refusal with the current step" to Flows 5–7 in sad §6. — owner: `sequences`, due: before the contract is finalized (before `/sdd:tasks telegram-link`)
- [ ] **Telegram not answering a wizard step** (§B 4, gap 2): add the branch and its timeout to Flows 5–7. Contract default: 503 `telegram-unavailable`, attempt kept at its step. — owner: `sequences`, due: before the contract is finalized (before `/sdd:tasks telegram-link`)

Next stage: `/sdd:screens telegram-link`.
