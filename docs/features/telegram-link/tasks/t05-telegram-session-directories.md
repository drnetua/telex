---
id: T5
title: "Manage Telegram session directories: per-session dir, destroy with one-minute retry, startup orphan sweep"
layer: "infra"
deps: ["T4"]
blocks: ["T6", "T10", "T11", "T12"]
acs: ["AC-111", "AC-112"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/TelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/internal/files/", "backend/app/src/main/kotlin/telex/telegram/internal/fake/", "backend/app/src/test/kotlin/telex/telegram/internal/files/"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T5 — Manage Telegram session directories: per-session dir, destroy with one-minute retry, startup orphan sweep

## Place in the sequence

- **Blocked by:** T4 — Define the TelegramSessions port, its in-process events and the fake Telegram adapter · **Blocks:** T6 — Implement the tdlight adapter: TDLib auth states, errors and chat list mapped to the port, T10 — Complete an authorized attempt: link a new account, sign in again, or refuse and log out, T11 — Unlink an account: bounded sign-out, one-transaction delete with AccountUnlinked, then destroy the session, T12 — Reconnect accounts on boot and follow Telegram's session state (Connected, Reconnecting, Session lost) · **Wave:** 2 — adds `sweepOrphans` to the port defined in T4 (same file).
- **Lane:** shares `telex/telegram/TelegramSessions.kt` with T4 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to unlink a Linked Account so that teleX signs out of it and deletes everything it stored for it
> **So that** no copy of my Telegram access stays in teleX
>
> — `spec.md §4, US-03, verbatim` · full text: [spec.md](../spec.md)

This task makes sure no session file outlives its account, even after a crash between the unlink commit and the file deletion.

## Inlined context

> **Session files.** A new named volume `telex-tdlib` is mounted at `/var/lib/telex/tdlib` (`TELEX_TELEGRAM_SESSIONS_DIR`), with one directory per `TelegramSessionId`.
> **Boot order.** The app starts, Flyway migrates, the session sweeper runs, then `messaging` reopens every non-lost Linked Account in parallel on virtual threads.
>
> — `sad.md §7, Session files + Boot order, abridged` · full text: [sad.md](../sad.md)

> Without that key, any session directory left on disk is unreadable (crypto-shredding […]). The startup sweep and a one-minute retry of failed deletions then remove such orphan directories.
>
> — `adr/0003 §Decision outcome, abridged` · full text: [0003-seal-each-tdlib-database-key-with-a-stored-per-owner-key-under-an-installation-master-key.md](../adr/0003-seal-each-tdlib-database-key-with-a-stored-per-owner-key-under-an-installation-master-key.md)

> │       └── files/                   session directory root, per-session dirs, orphan sweep at startup, one-minute retry of failed deletions
>
> — `sad.md §5, Internal decomposition, verbatim` · full text: [sad.md](../sad.md)

> **Hard rule:** `telegram` never sees an `OwnerId` or `LinkedAccountId`: the sweep takes the set of referenced `TelegramSessionId`s from its caller (messaging, T12).
>
> — `adr/0002 §Decision outcome, abridged` · full text: [0002-own-linked-accounts-and-their-chat-list-in-messaging-behind-a-session-only-telegram-acl.md](../adr/0002-own-linked-accounts-and-their-chat-list-in-messaging-behind-a-session-only-telegram-acl.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — no API surface. Port addition: `sweepOrphans(referenced: Set<TelegramSessionId>)`; `destroy(id)` now deletes the directory.

## Acceptance criteria

### AC-111 — happy

> **Given** an Owner with a connected Linked Account
> **When** they choose to unlink it and confirm in a dialog that names what will happen
> **Then** teleX signs out of that Telegram account so the teleX device disappears from the account's active sessions in Telegram, deletes the account's session and every chat-list entry it synced, and removes the account from the list; if it was the Owner's last Linked Account, the Inbox shows the "Connect Telegram" step again
>
> — `spec.md §5, AC-111, verbatim` · full text: [spec.md](../spec.md)

### AC-112 — cross-context

> **Given** an Owner who unlinks a Linked Account
> **When** the unlink completes, and also after teleX restarts, even if it stopped right after the unlink
> **Then** the account no longer appears anywhere in teleX that lists or offers the Owner's Linked Accounts and can't be chosen for anything; linking the same Telegram account again creates a new Linked Account with nothing attached; from E09 and E20 on, the same unlink pauses its agents and cancels their scheduled runs (AC-05, moved)
>
> — `spec.md §5, AC-112, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `internal/files/SessionDirectories`: root from `telex.telegram.sessions-dir` (`TELEX_TELEGRAM_SESSIONS_DIR`, default `/var/lib/telex/tdlib`; local profile a dir under the project), `create(id)`, `delete(id)`
- [ ] Failed deletions go to an in-memory retry set drained by a one-minute `@Scheduled` job (injectable `Clock`)
- [ ] `sweepOrphans(referenced)` on the port: delete every child dir whose name isn't a referenced session id
- [ ] The fake adapter also creates/deletes its directory (a marker file), so unlink and sweep tests work on `fake`
- [ ] Unit tests on a temp dir: create/destroy, failing delete retried, sweep keeps referenced and deletes the rest

## Edge cases

| Case | Behaviour |
|---|---|
| Delete fails (file locked) | Retried every minute; meanwhile unreadable because its sealed key is gone |
| Deletion keeps failing until restart | The startup sweep deletes it |
| Directory of an open attempt at restart | Not referenced by any Linked Account → swept (the attempt's key lived only in memory) |

## Definition of Done

- [ ] unit tests for create / destroy / retry / sweep pass
- [ ] `ModularityTest` green; detekt + ktlint clean
- [ ] every Hard Rule inlined above still holds
