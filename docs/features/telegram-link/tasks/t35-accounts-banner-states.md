---
id: T35
title: "SCR-60 and the banner: linked Toast, Reconnecting and Session lost notes, banner refusals, unlink focus, one-shot arrival Toast, icon and copy fixes"
layer: "ui"
deps: ["T34", "T29"]
blocks: ["T36", "T38"]
acs: ["AC-114", "AC-117", "AC-122", "AC-111", "AC-106"]
files_hint: ["frontend/src/pages/accounts/AccountsPage.tsx", "frontend/src/pages/accounts/AccountsPage.test.tsx", "frontend/src/components/LinkedAccountSummary/LinkedAccountSummary.tsx", "frontend/src/components/LinkedAccountSummary/LinkedAccountSummary.test.tsx", "frontend/src/components/StatusBanner/AccountDisconnectedBanner.tsx", "frontend/src/components/StatusBanner/StatusBanner.test.tsx", "frontend/src/pages/inbox/InboxPage.tsx", "frontend/src/pages/inbox/InboxPage.test.tsx", "frontend/src/pages/connect-telegram/PasswordStep.tsx", "frontend/src/pages/connect-telegram/Outcomes.tsx", "frontend/src/pages/connect-telegram/Outcomes.test.tsx", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-03 — findings S7, S8, S9, S11, Q8, Q16 (d)"
status: "todo"
---

# T35 — SCR-60 and the banner: linked Toast, Reconnecting and Session lost notes, banner refusals, unlink focus, one-shot arrival Toast, icon and copy fixes

## Origin

Follow-up from the independent review: [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md), findings **S7, S8, S9, S11, Q8, Q16 (d)** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md) and the states in [screens.md](../screens.md).

- **ACs:** AC-114, AC-117, AC-122, AC-111, AC-106
- **Blocked by:** T34, T29 · **Blocks:** T36, T38

## What to change

- SCR-60 `linked` state: AccountsPage never reads `location.state.toast` (ConnectTelegramPage `leave("accounts", toast)` sends it); show "`<displayName>` is connected (again)" as InboxPage does (AC-114, AC-117).
- SCR-60 rows: render `messages.accounts.reconnectingNote` / `sessionLostNote` (small text under the badge, `variant="row"`) per screens.md, and keep the last sync values while reconnecting (`SyncLine` currently returns null for non-connected). Line variant: use "x of y chats" (`messages.accounts.chatsOf`) per W-10b.
- Banner "Sign in again" (`AccountDisconnectedBanner.tsx:25-29`): handle refusals per SCR-60's `start-refused` row — reuse AccountsPage's `refusalFor` (Toast, plus list invalidation on 404 and already-linked).
- Unlink dialog: return focus to the page h1 / card heading when the unlinked row is gone (`ConfirmDialog` `returnFocusTo`, as PasskeysCard.tsx:127 does).
- Arrival Toast (InboxPage and now AccountsPage) must not re-appear on reload/Back: clear `history.state.toast` after reading (`navigate(".", {replace:true, state:null})`).
- Component fixes per screens.md: PasswordStep hint line gets `Icon info-circle`; refused-mismatch uses `ban` not `alert-circle`; wait-state "Back" is secondary not ghost.

## RED first

Vitest for each: Accounts shows the arrival Toast; reconnecting/session-lost notes rendered and sync values kept; banner refusal Toast; focus lands on heading after unlink; Toast not shown after a re-render with cleared state; icons/variants asserted.

## Definition of Done

Every SCR-60 and banner state in screens.md renders; a11y focus correct. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
