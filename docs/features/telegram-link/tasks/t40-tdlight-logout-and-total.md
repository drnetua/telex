---
id: T40
title: "Real adapter: logOut is false for a closed session, and the chat total ignores folder counts"
layer: "infra"
deps: []
blocks: ["T45"]
acs: ["AC-113", "AC-116"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessions.kt", "backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightTelegramSessionsTest.kt", "backend/app/src/test/kotlin/telex/telegram/internal/fake/FakeTelegramTest.kt"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 — findings N2, N3"
status: "todo"
---

# T40 — Real adapter: logOut is false for a closed session, and the chat total ignores folder counts

## Origin

Follow-up from the independent re-review: [`_review/review-2026-10-04.md`](../_review/review-2026-10-04.md), findings **N2, N3** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-113, AC-116
- **Blocked by:** — · **Blocks:** T45

## What to change

- `TdlightTelegramSessions.logOut` returns `false` only for an unknown id. A session still registered but already closed (remote termination before `SessionStateListener` closes it, `onWaitPhone`, WaitRegistration) answers `true` at once because `closed` is already complete, so unlink reports `signOutConfirmed: true` though nothing was signed out. Return `false` unless the session is authorized/open before sending `LogOut`, matching the port doc (`TelegramSessions.kt`) and `FakeTelegram.logOut`.
- `TdlightSession.total()` returns `counts.values.sum()`, and `counts` also holds `TdlibChatList.Folder` counts mapped from `UpdateUnreadChatCount`; folder members are already in Main or Archive, so "x of y" is inflated. Use Main + Archive only (or drop Folder counts when storing).

## RED first

Unit: a closed-but-registered session's `logOut` returns false (red today). Unit: emit Main, Archive and a Folder `ChatCount`; the reported total is Main + Archive (red today).

## Definition of Done

Fake and real adapters agree on `logOut` for every not-open session; the total is Telegram's Main + Archive count. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
