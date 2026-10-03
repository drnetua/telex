---
id: T6
title: "Implement the tdlight adapter: TDLib auth states, errors and chat list mapped to the port"
layer: "infra"
deps: ["T1", "T5"]
blocks: ["T17"]
acs: ["AC-36", "AC-121"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/", "backend/telegram-tdlib/", "backend/app/src/integrationTest/kotlin/telex/telegram/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T6 — Implement the tdlight adapter: TDLib auth states, errors and chat list mapped to the port

## Place in the sequence

- **Blocked by:** T1 — Prove TDLight on JDK 25 and land the binding behind the TdlibFacade (spike), T5 — Manage Telegram session directories: per-session dir, destroy with one-minute retry, startup orphan sweep · **Blocks:** T17 — Wire the Operator config, the session volume and the README Telegram setup step · **Wave:** 3 — needs the facade from the spike (T1) and the full port including directories (T5).
- **Lane:** shares `backend/telegram-tdlib/` with T1 — serialized.

## Why (user story)

> **As an** Owner
> **I want** my Linked Accounts to come back on their own after teleX restarts
> **So that** I never have to type a Telegram code again just because the server restarted
>
> — `spec.md §4, US-52, verbatim` · full text: [spec.md](../spec.md)

This task connects the port to real Telegram, so accounts actually reconnect with their stored session and the chat list follows Telegram's changes.

## Inlined context

> **Telegram decides the state, and the browser hears it live.** The account state follows TDLib's own signals:
> - authorization ready → Connected;
> - connection lost → Reconnecting;
> - only authorization closed (the session was ended in or by Telegram) → Session lost.
>
> — `sad.md §4, choice 4, verbatim` · full text: [sad.md](../sad.md)

> Concurrency: Telegram callbacks arrive on TDLib's threads. The adapter hands each one to a virtual thread, and `messaging` applies state changes per account in order (state changes carry TDLib's sequence, and stale ones are dropped).
> Logging: **Never logged:** phone numbers, login codes, passwords, password hints, TDLib keys, Telegram names, chat titles, raw TDLib objects. […] TDLib's own log goes to the app log at verbosity 1 (errors only).
> Telegram wait (flood wait): Not stored by teleX. […] teleX adds no retries.
>
> — `sad.md §8, Concurrency + Logging + Telegram wait, abridged` · full text: [sad.md](../sad.md)

> Flow 11: S->>X: load the main and archived chat lists / X-->>S: chats changed, a batch plus the account's total / […] while the account is connected: a chat joined, left or renamed, or new messages changed a chat's order or unread count.
>
> — `sad.md §6, Flow 11, abridged` · full text: [sad.md](../sad.md)

> QG-1a: An integration test with the `tdlight` adapter against a stub directory, or the spike's real session, captures the raw TDLib key in memory. It then searches every column of `linked_account`, `owner_key`, `event_publication` and the captured log output, and every byte of the session volume, for that key. Zero hits.
>
> — `sad.md §10, QG-1a How verify, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** Only `telex.telegram.tdlib` imports the binding; the adapter talks to `TdlibFacade` types only.
>
> — `adr/0004 §Decision outcome, abridged` · full text: [0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md](../adr/0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — no API surface. Implements the T4/T5 `TelegramSessions` port with the outcome names fixed there.

## Acceptance criteria

### AC-36 — happy

> **Given** an Owner with 2 connected Linked Accounts
> **When** teleX restarts
> **Then** both accounts are connected again without the Owner typing any code
>
> — `spec.md §5, AC-36, verbatim` · full text: [spec.md](../spec.md)

### AC-121 — happy

> **Given** an Owner with a connected Linked Account whose chat list has finished syncing
> **When** in Telegram they join or leave a chat, a chat is renamed, or new messages arrive
> **Then** within one minute the number of chats shown for the account reflects the change, and the chat list teleX keeps for the account stays current while it is connected; showing that list and the messages is E04
>
> — `spec.md §5, AC-121, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `internal/tdlight/TdlightTelegramSessions`: one facade client per session dir, opened with `api_id`/`api_hash` and the 32-byte key; callbacks handed to a virtual thread
- [ ] Map auth states: wait phone/code/password(hint) → step outcomes; ready → `Authorized` + `Ready`; closed/logging out → `Closed`; connection state → `Ready`/`Connecting`; a per-session increasing `sequence`
- [ ] Map errors: invalid phone, banned, flood wait (`seconds`) to the port outcomes; wrong/expired code; wrong password (+ hint); a step not answered in time → `TelegramUnavailable`. Unregistered number: per `spike.md`, either at the phone step or as the registration state after the code — map both to `PhoneUnregistered` and close the session
- [ ] `Authorized.user`: Telegram user id, first + last name, calling code (TDLib phone-number info) + last two digits — never the full number
- [ ] Chat list: load main + archive lists, emit `TelegramChatsChanged` batches with `total` and `loadCompleted`; then forward chat added/removed/title/position/unread updates
- [ ] `logOut(id, timeout)` waits for closed or the timeout; `close` / `destroy` release the client and the directory
- [ ] IT (`backend/app/src/integrationTest/kotlin/telex/telegram/TdlibKeyAtRestIT.kt`, natives required, skip where unsupported): start a client on a temp dir, capture the raw key, search DB columns, captured logs and every byte of the dir — zero hits (QG-1a)

## Edge cases

| Case | Behaviour |
|---|---|
| Telegram unreachable at reopen | `Connecting`, TDLib retries with backoff; nothing asked of the Owner |
| Session ended while teleX was stopped | `Closed` on reopen (AC-118 via T12) |
| Flood wait on any step | `WaitRequired(seconds)` — no retry by teleX |

## Definition of Done

- [ ] unit tests for the state and error mapping pass; QG-1a IT passes on linux x64 CI
- [ ] manual: link + restart on a test account reconnects without a code (recorded for the E02 PR)
- [ ] no `it.tdlight.*` import outside `telex.telegram.tdlib`; detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
