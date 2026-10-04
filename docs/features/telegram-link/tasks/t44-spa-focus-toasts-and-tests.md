---
id: T44
title: "SPA polish: unlink focus, a dismissible SCR-02 load-failure Toast with Retry, one Toast slot, one refusal lookup, stronger tests"
layer: "ui"
deps: ["T43"]
blocks: ["T45"]
acs: ["AC-111", "AC-114", "AC-119", "AC-116"]
files_hint: ["frontend/src/pages/accounts/AccountsPage.tsx", "frontend/src/pages/accounts/AccountsPage.test.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx", "frontend/src/components/Toast/Toast.tsx", "frontend/src/components/Toast/Toast.test.tsx", "frontend/src/pages/inbox/InboxPage.tsx", "frontend/src/pages/inbox/InboxPage.test.tsx", "frontend/src/api/linkingRefusal.ts", "frontend/src/components/LinkedAccountSummary/LinkedAccountSummary.test.tsx", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-04 — findings F1, F2, F3, F4, T4, T5"
status: "done"
---

# T44 — SPA polish: unlink focus, a dismissible SCR-02 load-failure Toast with Retry, one Toast slot, one refusal lookup, stronger tests

## Origin

Follow-up from the independent re-review: [`_review/review-2026-10-04.md`](../_review/review-2026-10-04.md), findings **F1, F2, F3, F4, T4, T5** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-111, AC-114, AC-119, AC-116
- **Blocked by:** T43 · **Blocks:** T45

## What to change

- F1 `AccountsPage`: `unlinked.current` is set on success and never reset, so after one unlink a cancelled dialog sends focus to the h1; on a 404 "unlink-gone" focus returns to a row about to vanish. Reset it when a dialog opens and set it on the 404 path (WCAG 2.4.3).
- F2 `ConnectTelegramPage`: the load-failure Toast's `onDismiss` only clears `notice`, so it can't be closed; it offers no Retry and the skeleton stays `aria-busy`. Track it separately, add a Retry action (`query.refetch()`), and use copy that says what failed (design-system README §Content).
- F3: each Toast opens its own fixed `toast-container`, so Toasts open together overlap. Use one shared container/slot (stack, or newest replaces older).
- F4: three copies of the start-refusal lookup (`InboxPage`, `ConnectTelegramPage.startRefusal`, `api/linkingRefusal.ts` `refusalFor`); the Inbox copy ignores function-valued messages. Use `refusalFor` everywhere.
- T4: the SCR-02 load-503 test renders without `FailureBoundary` and asserts only no crash; render with `createAppQueryClient()` + `FailureBoundary` and assert SCR-93 shows and the wizard is hidden.
- T5: the SCR-10 "x of y chats" line variant (`chatsOf`) has no test.

## RED first

Vitest for each: second dialog cancelled → focus on that row's Unlink button; 404 unlink → focus on h1; load-failure Toast closes and Retry refetches; two Toasts at once are both readable (one container); Inbox shows the limit refusal text from `refusalFor`; the strengthened 503 and `chatsOf` tests.

## Definition of Done

All four UI defects fixed with tests; the two tests can fail on their regressions; `pnpm run check` clean. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
