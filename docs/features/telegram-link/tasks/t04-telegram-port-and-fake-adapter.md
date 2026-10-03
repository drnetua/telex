---
id: T4
title: "Define the TelegramSessions port, its in-process events and the fake Telegram adapter"
layer: "infra"
deps: []
blocks: ["T5", "T8", "T13"]
acs: ["AC-122"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/TelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/TelegramSessionId.kt", "backend/app/src/main/kotlin/telex/telegram/SignInOutcome.kt", "backend/app/src/main/kotlin/telex/telegram/TelegramSessionStateChanged.kt", "backend/app/src/main/kotlin/telex/telegram/TelegramChatsChanged.kt", "backend/app/src/main/kotlin/telex/telegram/internal/fake/", "backend/app/src/main/kotlin/telex/telegram/internal/TelegramAdapterConfiguration.kt", "backend/app/src/test/kotlin/telex/telegram/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T4 — Define the TelegramSessions port, its in-process events and the fake Telegram adapter

## Place in the sequence

- **Blocked by:** — · **Blocks:** T5 — Manage Telegram session directories: per-session dir, destroy with one-minute retry, startup orphan sweep, T8 — Start, resume, cancel and expire the in-memory linking attempt (one per Owner), T13 — Sync each account's chat list into channel rows with throttled progress events · **Wave:** 1 — no deps — the port is pure `telegram` + `shared`, so it starts in wave 1 and unblocks all `messaging` work while the spike runs.
- **Lane:** shares `telex/telegram/TelegramSessions.kt` with T5 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to see whether each Linked Account is connected (and whether it is still syncing its chats), is reconnecting after Telegram was unreachable, or has lost its session, and to sign in to it again when it has
> **So that** I know when teleX can work with an account and fix it without starting over
>
> — `spec.md §4, US-51, verbatim` · full text: [spec.md](../spec.md)

This task gives every higher layer a Telegram it can drive deterministically, so each AC is testable without real Telegram or native code.

## Inlined context

> The `telegram` port has two adapters, chosen by `telex.telegram.adapter`:
> - `tdlight` (default) wraps the facade;
> - `fake` is an in-memory Telegram with fixed codes, a scriptable two-step password, flood waits, banned numbers, session termination and a chat list of a chosen size.
> `@ApplicationModuleTest`s, Playwright e2e and `bootRun` without Telegram credentials use `fake`.
>
> — `adr/0004 §Decision outcome, abridged` · full text: [0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md](../adr/0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md)

> │   ├── TelegramSessions             port: open, phone, code, resend, password, log out, close and destroy
> │   ├── TelegramSessionId, SignInOutcome, ChatSnapshot          port types
> │   ├── TelegramSessionStateChanged, TelegramChatsChanged       events (session id, never an Owner id)
> Ids that cross into `telegram` are `telegram`'s own (`TelegramSessionId`, Telegram user and chat ids). `OwnerId` and `LinkedAccountId` never enter it.
>
> — `sad.md §5, Internal decomposition, abridged` · full text: [sad.md](../sad.md)

> Channel 2: in-process, non-durable (plain Spring application events). Producer: `telegram` (TDLib callbacks handed to a virtual thread). Consumer: `messaging` via a synchronous `@EventListener` (not `@ApplicationModuleListener`), so nothing is written to `event_publication`.
> `TelegramSessionStateChanged(sessionId: TelegramSessionId, state: Ready | Connecting | Closed, sequence: Long)` — Only Closed means Session lost, so an outage never looks like a lost session (AC-122).
> `TelegramChatsChanged(sessionId, upserted: List<ChatSnapshot>, removedChatIds: List<Long>, total: Int?, loadCompleted: Boolean)`, where `ChatSnapshot(chatId, type, title, folderIds, archived, unreadCount, order)` mirrors the `channel` columns.
>
> — `contracts/events.md §Channel 2, abridged` · full text: [events.md](../contracts/events.md)

> **Hard rule:** `telegram` may depend on `shared` only; it never sees an `OwnerId` or a `LinkedAccountId`. Telegram data never enters `event_publication`.
>
> — `sad.md §2 Module rules + events.md §intro, abridged` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — no API surface. Port shape this task fixes (refine names, keep the outcomes):
- `configured(): Boolean` (api_id/api_hash present — AC-119), `open(dbKey): TelegramSessionId`, `reopen(id, dbKey)`
- `sendPhone(id, digits)` → `CodeSent(codeLength) | PhoneInvalid | PhoneUnregistered | PhoneBanned | WaitRequired(seconds)`
- `resendCode(id)`, `checkCode(id, code)` → `PasswordNeeded(hint?) | Authorized(user) | CodeWrong | CodeExpired | WaitRequired`
- `checkPassword(id, pw)` → `Authorized(user) | PasswordWrong(hint?) | WaitRequired`; `Authorized.user` = Telegram user id, display name, phone country code + last two digits (the full number never leaves `telegram`)
- `logOut(id, timeout): Boolean` (confirmed), `close(id)`, `destroy(id)`; a step Telegram doesn't answer in time → `TelegramUnavailable` (→ 503 `telegram-unavailable`)

## Acceptance criteria

### AC-122 — error

> **Given** an Owner with a connected Linked Account
> **When** teleX temporarily can't reach Telegram, or Telegram confirms that the account's session has ended
> **Then** while Telegram is unreachable the account shows "Reconnecting" on the Accounts page and teleX reconnects on its own without asking the Owner for anything; only when Telegram confirms the session has ended does the account show "Session lost", and then every signed-in screen also shows a Status Banner saying the account is disconnected, with a "Sign in again" action, until the Owner signs in again or unlinks it
>
> — `spec.md §5, AC-122, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Port types + `TelegramSessions` interface at `telex.telegram` root; `TelegramSessionId` as a typed UUIDv7 id (`telex.shared.TypedId`)
- [ ] Events `TelegramSessionStateChanged` / `TelegramChatsChanged` as plain Spring events (no Modulith externalization)
- [ ] `internal/fake/FakeTelegram`: behaviour scripted by test phone numbers in Telegram's test shape `99966XYYYY` (e.g. a digit selects 2FA, banned, unregistered, flood wait, N chats, terminate-after-link), fixed codes, a code length, a password hint; test hooks to drop connectivity and terminate a session
- [ ] Fake emits `Ready` after authorization, then chat batches with `total` and `loadCompleted`; `Connecting` on a dropped connection and `Ready` again; `Closed` on termination; a strictly increasing `sequence` per session
- [ ] `TelegramAdapterConfiguration`: `telex.telegram.adapter` = `tdlight` (default) | `fake`; bind `telex.telegram.api-id` / `api-hash` for `configured()`
- [ ] Unit tests in `backend/app/src/test/kotlin/telex/telegram/` for every scripted branch of the fake

## Edge cases

| Case | Behaviour |
|---|---|
| Connectivity dropped | `Connecting` only — never `Closed` (AC-122) |
| Session terminated in "Telegram" | `Closed` with a newer sequence |
| `logOut` on an unreachable fake | returns `false` after the timeout (feeds AC-113) |

## Definition of Done

- [ ] fake-adapter unit tests cover every outcome listed in the port shape
- [ ] `ModularityTest` green: `telegram` depends on `shared` only
- [ ] detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
