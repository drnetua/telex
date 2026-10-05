---
id: T43
title: "Report Session lost through the shell's Status Banner, with the account name and a working Sign in again"
layer: "ui"
deps: []
blocks: ["T44", "T45"]
acs: ["AC-122", "AC-117"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/internal/account/SessionLostConditions.kt", "backend/app/src/main/kotlin/telex/shared/StatusConditionSource.kt", "backend/app/src/main/kotlin/telex/web/api/PulseController.kt", "backend/app/src/integrationTest/kotlin/telex/web/PulseIT.kt", "frontend/src/shell/conditions.ts", "frontend/src/shell/conditions.test.ts", "frontend/src/shell/StatusBanner/StatusBanner.tsx", "frontend/src/shell/StatusBanner/StatusBanner.test.tsx", "frontend/src/shell/AppShell/AppShell.tsx", "frontend/src/app/layouts.tsx", "frontend/src/app/layouts.test.tsx", "frontend/src/components/StatusBanner/AccountDisconnectedBanner.tsx", "frontend/src/components/StatusBanner/StatusBanner.test.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx", "frontend/src/messages.ts", "e2e/tests/telegram-link.spec.ts", "docs/design-system.md"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-04 — findings N6, N7"
status: "done"
---

# T43 — Report Session lost through the shell's Status Banner, with the account name and a working Sign in again

## Origin

Follow-up from the independent re-review: [`_review/review-2026-10-04.md`](../_review/review-2026-10-04.md), findings **N6, N7** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-122, AC-117
- **Blocked by:** — · **Blocks:** T44, T45

## What to change

- f50946b kept telegram-link's own C-04 port (`components/StatusBanner/AccountDisconnectedBanner`) inside the page content, beside the shell's banner. That deviates from app-shell ADR-0006 (Accepted): E02 must report `account-disconnected` through a `StatusConditionSource` that `web` collects into the pulse, and the SPA maps the code via the one catalog in `shell/conditions.ts`. Today banners stack when offline + Session lost, and the always-rendered `mb-3` wrapper adds a gap on every page.
- Backend: a `StatusConditionSource` bean in `messaging` returns `account-disconnected` while the Owner has a `session_lost` Linked Account (Owner-scoped). Cover it in the pulse IT.
- SPA: drop the page-level port from `layouts.tsx`. Extend the shell's `account-disconnected` catalog entry so the banner keeps AC-122/screens.md behaviour: the message names the account (one Session lost account → its display name from `listMyLinkedAccounts`; several → the plural copy), and the action is "Sign in again" that starts a targeted attempt (one account) or opens Accounts (several), with the refusal Toast + list invalidation T35 added. Keep the change to the shell minimal and generic (e.g. a condition can carry a dynamic message/action resolved by a small registry) — the shell's banner code must not learn telegram-link specifics. Importance order unchanged (offline/not-responding first, then account-disconnected, "+N more").
- N6: the banner's "Sign in again" today only navigates to `/connect-telegram`; on SCR-02 showing an outcome card nothing visible happens and the new target attempt is left open. On success put the attempt in the `linkingAttemptKey` cache (as SCR-10/SCR-60 do) and make ConnectTelegramPage reset `outcome`/last state when a fresh attempt lands.
- Update the design-system inventory row (C-04 port → the shell banner) and delete the dead port and its now-unused messages.

## RED first

IT: pulse returns `account-disconnected` for an Owner with a session_lost account and not for another Owner. Vitest: shell banner with `account-disconnected` shows the account name and Sign in again starts a targeted attempt; offline + account-disconnected shows one banner with "+1 more"; layouts render no page-level banner and no empty wrapper; banner Sign in again on the SCR-02 ended card shows the target's step. Update the e2e Session lost / banner paths to the shell banner.

## Definition of Done

One C-04 banner in the shell for every condition including Session lost; AC-122 banner behaviour unchanged for the Owner; N6 fixed; e2e green at both widths. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
