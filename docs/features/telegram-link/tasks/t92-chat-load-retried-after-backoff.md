---
id: T92
title: "A chat-list load that fails while the connection stays up is retried after a bounded backoff"
layer: "infra"
deps: []
blocks: ["T93", "T95"]
acs: ["AC-116", "AC-121"]
files_hint: ["backend/app/src/main/kotlin/telex/telegram/internal/tdlight/TdlightSession.kt", "backend/app/src/test/kotlin/telex/telegram/internal/tdlight/TdlightChatSyncTest.kt"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (twelfth pass) — W23"
status: "done"
---

# T92 — A chat-list load that fails while the connection stays up is retried after a bounded backoff

## Origin

Follow-up from the twelfth-pass review: [`_review/review-2026-10-05-r4.md`](../_review/review-2026-10-05-r4.md), W23 (resolved "Fix now" by the user). Read that row first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-116, AC-121
- **Blocked by:** — · **Blocks:** T93, T95

## What to change

- The T87 restart fires only from `onConnection`. A load that fails while TDLib stays Ready (a `429 "Too Many Requests: retry after N"` from a flood during a large first sync, a non-404 error, the 60 s client timeout) or whose Ready arrived while the old load was still blocked is never restarted (`TdlightSession.kt:268-274,415-448`).
- In the `catch`, when the session is authorized, connected (last state Ready), not closing and not logging out, schedule one retry after a bounded backoff: the 429's `retry after N` seconds when given (cap it), else a fixed delay that grows per failure up to a cap. The retry goes through `resumeChatLoad`, so it keeps one load at a time and its guards. Never retry in a tight loop.
- Keep the delay injectable or small enough for a unit test.

## RED first

Unit, in `TdlightChatSyncTest`: `LoadChats(Main)` answers `Failure(429, "Too Many Requests: retry after 1")` once, with no connection update; the load then completes and a later chat change is stored. A second case: the session is closed while the retry waits, and no new `LoadChats` is sent.

## Definition of Done

A chat-list load that stops while the session is authorized and connected is retried once after a bounded backoff (Telegram's retry-after when given), one load at a time, never after close or during teleX's own log out. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)

## How it was done

- The retry waits on the session's `over` future, so a close wakes it early and no request is sent. When it wakes, it calls `resumeChatLoad` only while the last state is still Ready. Non-429 failures back off 5 s, doubling up to 300 s. A 429's `retry after N` is clamped to 1–300 s. The counter resets when a load completes.
- The DoD rules out a retry during teleX's own log out, so the `loggingOut` guard (planned for T93) went into `resumeChatLoad` here. A third test covers it: a log out starts while the retry waits, and no new `LoadChats` is sent. Mutation: with the guard removed, that test fails.
- The close test passes if either guard stays: the early wake on `over`, or `isAuthorized()` in `resumeChatLoad`. T93's "fail, close, then Ready" test checks `isAuthorized()` on its own.
