---
id: T49
title: "Banner: no false generic text, Sign in again outcome survives reorder and refetch, Toasts are announced"
layer: "ui"
deps: []
blocks: ["T50"]
acs: ["AC-122", "AC-117", "AC-111", "AC-114"]
files_hint: ["frontend/src/shell/accountDisconnected.tsx", "frontend/src/shell/StatusBanner/StatusBanner.tsx", "frontend/src/shell/StatusBanner/StatusBanner.test.tsx", "frontend/src/shell/conditions.ts", "frontend/src/components/Toast/Toast.tsx", "frontend/src/components/Toast/Toast.test.tsx", "frontend/src/pages/accounts/AccountsPage.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-04 (second pass) — findings B2, B3, B5"
status: "todo"
---

# T49 — Banner: no false generic text, Sign in again outcome survives reorder and refetch, Toasts are announced

## Origin

Follow-up from the second-pass review: [`_review/review-2026-10-04-r2.md`](../_review/review-2026-10-04-r2.md), findings **B2, B3, B5** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-122, AC-117, AC-111, AC-114
- **Blocked by:** — · **Blocks:** T50

## What to change

- B2: `useLive` in `accountDisconnected.tsx` returns `{}` both while the list loads and when the loaded list has no Session lost account, so the catalog fallback "Your account is disconnected from Telegram." shows falsely for up to 3 s after Sign in again or the last unlink (and the polite region announces it). Use the fallback only while the list is undefined; when loaded data contradicts the pulse, suppress the condition through a small shell-generic flag (e.g. `inactive`) — keep the shell free of telegram-link specifics. Also invalidate the pulse query after Sign in again succeeds and after an unlink.
- B3: the banner's Sign in again `mutate()` callbacks and the `refusal` state live inside the `ConditionLine` keyed by `top.code`; when connectivity outranks the condition mid-request, or the refetch clears the lost account (409 already-linked from another tab), the line unmounts — TanStack v5 drops mutate-level callbacks, so no navigation, no cache write, no Toast. Move onSuccess/onError into `useMutation` options (or a module-level handler) and keep the refusal Toast in state that outlives the line (e.g. the shared Toast slot / a small store).
- B5: info Toasts arrive as a new container plus a new `role=status` node with text already set, which screen readers often don't announce. Keep one persistent, empty polite live region mounted for the app lifetime and write each Toast's text into it after mount (error Toasts with role=alert stay as they are).

## RED first

Vitest: list loaded with no lost account while pulse still reports the code → no banner text (red today). Vitest: start Sign in again, flip connectivity to not-responding before the mutation resolves → navigation + cache write still happen; 409 already-linked whose refetch clears the lost account → refusal Toast still shown. Vitest: an info Toast's text appears inside a live region that existed before the Toast mounted.

## Definition of Done

The banner never shows text its data contradicts; the action's outcome is never lost; info Toasts are announced. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
