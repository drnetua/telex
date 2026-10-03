---
id: T24
title: "Prove nothing is left behind or leaked: unlink dump, restart delivery, registry and log scans"
layer: "tests"
deps: ["T10", "T11", "T12", "T13", "T16"]
blocks: []
acs: ["AC-03", "AC-112", "AC-120"]
files_hint: ["backend/app/src/integrationTest/kotlin/telex/messaging/NothingLeftBehindIT.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/UnlinkAnnouncementRestartIT.kt", "backend/app/src/integrationTest/kotlin/telex/TelegramDataHygieneIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T24 — Prove nothing is left behind or leaked: unlink dump, restart delivery, registry and log scans

## Place in the sequence

- **Blocked by:** T10 — Complete an authorized attempt: link a new account, sign in again, or refuse and log out, T11 — Unlink an account: bounded sign-out, one-transaction delete with AccountUnlinked, then destroy the session, T12 — Reconnect accounts on boot and follow Telegram's session state (Connected, Reconnecting, Session lost), T13 — Sync each account's chat list into channel rows with throttled progress events, T16 — Serve the live-update SSE stream of invalidation hints per Owner · **Blocks:** — · **Wave:** 6 — needs the full backend path: link (T10), unlink (T11), lifecycle (T12), sync (T13), live listeners (T16).
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** to unlink a Linked Account so that teleX signs out of it and deletes everything it stored for it
> **So that** no copy of my Telegram access stays in teleX
>
> — `spec.md §4, US-03, verbatim` · full text: [spec.md](../spec.md)

This task is the automated half of the evidence that an unlink leaves nothing and the Operator sees no Owner's Telegram data.

## Inlined context

> QG-1b: an integration test with the `fake` adapter unlinks in both cases (confirmed and unreachable). It asserts no `linked_account` or `channel` row, no sealed key and no fake-Telegram session remain. A second test kills the process after the commit and before the directory is deleted, restarts, and asserts that the sweeper removed the directory.
> QG-1c: An integration test registers a test listener for `AccountUnlinked`, stops the context after the commit and before delivery, starts it again, and asserts delivery from `event_publication`.
>
> — `sad.md §10, QG-1b + QG-1c How verify, abridged` · full text: [sad.md](../sad.md)

> Non-runtime N/A: "the Operator sees no Telegram name, phone number or chat" (AC-03 second half, AC-120 second half). […] checked by scanning captured logs and metric tags in an integration test and by the security review.
> A test asserts that no `event_publication.serialized_event` contains a Telegram id or title after link, sync and unlink (QG-1b).
>
> — `sad.md §6 Coverage + events.md §Schema registry, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** Unlink leaves nothing: 0 stored items that identify the Telegram account […] for an unlinked account, always.
>
> — `spec.md §6, abridged` · full text: [spec.md](../spec.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes. (Reads every row of `linked_account`, `channel`, `owner_key`, `event_publication` for the assertions.)

## API contract

Internal — no API surface (drives `messaging` public API and, where simpler, the REST endpoints).

## Acceptance criteria

### AC-03 — authorization

> **Given** two Owners, each with their own Linked Account
> **When** one Owner tries to see, re-sign-in to or unlink the other Owner's Linked Account, or to see any chat synced for it, by any means
> **Then** teleX behaves as if that account and those chats don't exist; and the Operator sees no Telegram name, phone number or chat of any Owner's Linked Account
>
> — `spec.md §5, AC-03, verbatim` · full text: [spec.md](../spec.md)

### AC-112 — cross-context

> **Given** an Owner who unlinks a Linked Account
> **When** the unlink completes, and also after teleX restarts, even if it stopped right after the unlink
> **Then** the account no longer appears anywhere in teleX that lists or offers the Owner's Linked Accounts and can't be chosen for anything; linking the same Telegram account again creates a new Linked Account with nothing attached; from E09 and E20 on, the same unlink pauses its agents and cancels their scheduled runs (AC-05, moved)
>
> — `spec.md §5, AC-112, verbatim` · full text: [spec.md](../spec.md)

### AC-120 — happy

> **Given** an installation whose Operator has followed the README step that gives it Telegram app credentials
> **When** an Owner chooses "Connect Telegram"
> **Then** the linking wizard starts, and nothing in the Operator's setup or in what the Operator can see shows any Owner's Telegram name, phone number or chats
>
> — `spec.md §5, AC-120, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `NothingLeftBehindIT`: link + sync 50 chats, unlink confirmed and unreachable; assert no rows, no session dir, fake lists no session
- [ ] Crash case: commit the unlink, skip the directory destroy (test hook), restart the context, assert the sweep removed the dir
- [ ] `UnlinkAnnouncementRestartIT`: test listener for `AccountUnlinked`, stop after commit before delivery, restart, assert delivery (QG-1c)
- [ ] `TelegramDataHygieneIT`: after link, sync, wizard errors and unlink, scan `event_publication.serialized_event`, captured logs (`OutputCaptureExtension`) and `MeterRegistry` tags for the fake account's phone digits, name, Telegram ids, chat titles, code and password — zero hits

## Edge cases

| Case | Behaviour |
|---|---|
| Telegram unreachable at unlink | Still nothing stored; the device may remain (AC-113) |
| Restart between commit and listener | Republished on restart and delivered once more |

## Definition of Done

- [ ] all three ITs pass in `./gradlew integrationTest`
- [ ] the manual dump + active-sessions check is listed for the E02 PR
- [ ] every Hard Rule inlined above still holds
