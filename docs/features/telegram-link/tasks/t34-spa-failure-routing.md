---
id: T34
title: "SPA failures: domain 503 refusals stay on the page, SCR-02 never throws in render, step errors always give feedback, cache the new account"
layer: "ui"
deps: []
blocks: ["T29", "T35"]
acs: ["AC-119", "AC-109", "AC-01"]
files_hint: ["frontend/src/api/client.ts", "frontend/src/app/queryClient.ts", "frontend/src/app/FailureBoundary.tsx", "frontend/src/api/linking.test.ts", "frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx", "frontend/src/pages/connect-telegram/PhoneStep.tsx", "frontend/src/pages/connect-telegram/CodeStep.tsx", "frontend/src/pages/connect-telegram/PasswordStep.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx", "frontend/src/pages/inbox/InboxPage.test.tsx", "frontend/src/pages/accounts/AccountsPage.test.tsx", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-03 — findings S6, Q1, Q16 (a–c)"
status: "todo"
---

# T34 — SPA failures: domain 503 refusals stay on the page, SCR-02 never throws in render, step errors always give feedback, cache the new account

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **S6, Q1, Q16 (a–c)** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md) and the states in [screens.md](../screens.md).

- **ACs:** AC-119, AC-109, AC-01
- **Blocked by:** — · **Blocks:** T29, T35

## What to change

- `routeFor` gives every status ≥500 the route `unavailable`, so `503 telegram-linking-not-set-up` (and `telegram-unavailable`) from `useStartLinking()` goes through `MutationCache.onError` → `failureBus` → `FailureBoundary` shows SCR-93 over the page and hides the not-set-up Toast (AC-119; openapi: refusals before the wizard opens are "shown where the Owner chose the action"). Give no route to the screen-handled 503 codes (or skip the bus for them). Fix `linking.test.ts:107-110` which asserts the wrong route. Add tests that render InboxPage/AccountsPage INSIDE `FailureBoundary`.
- `ConnectTelegramPage.tsx:157` throws `query.error` during render with no React error boundary anywhere → a 5xx/403 on the attempt query (including a refocus refetch mid-wizard) unmounts the whole root (blank SPA). Don't throw: keep the last known step/loading and leave routable errors to the bus; unroutable ones to an explicit error state.
- Step-mismatch refetch (`ConnectTelegramPage.tsx:133-136`) has no error path — add a catch through `outcomeOf`/`routeFailure`. Async step handlers (`PhoneStep.tsx:39`, `CodeStep.tsx:44/58`, `PasswordStep.tsx:45`, `ConnectTelegramPage.tsx:106/146`) `throw e` into a voided promise → no feedback; show a generic error Toast (copy in messages.ts) instead.
- After linking, `finished` fetches the list without the query client, so the Inbox first renders the stale pre-link list; `client.setQueryData(linkedAccountsKey, list)` before leaving.

## RED first

Vitest: Inbox/Accounts inside FailureBoundary + 503 not-set-up → Toast visible, no SCR-93; ConnectTelegramPage with a 503 on load → no throw, page still rendered; unmapped 4xx in a step → error Toast; after link the Inbox shows the new account on first render.

## Definition of Done

AC-119 refusal visible in the real app shell; no render-throw; every failure gives feedback. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
