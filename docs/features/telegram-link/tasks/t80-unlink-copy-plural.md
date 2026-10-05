---
id: T80
title: "The unlink dialog names the chat count in correct English, and leaves it out at zero"
layer: "ui"
deps: []
blocks: ["T82"]
acs: ["AC-111"]
files_hint: ["frontend/src/messages.ts", "frontend/src/pages/accounts/AccountsPage.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (ninth pass) — W17"
status: "todo"
---

# T80 — The unlink dialog names the chat count in correct English, and leaves it out at zero

## Origin

Follow-up from the ninth-pass review: [`_review/review-2026-10-05.md`](../_review/review-2026-10-05.md), W17 (resolved "Fix now" by the user). Read those rows first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-111
- **Blocked by:** — · **Blocks:** T82

## What to change

- `messages.ts:325-326` `unlinkBody` reads "the ${chats} chats it synced", which gives "the 1 chats" and "the 0 chats".
- Reuse `accounts.chats(n)` for the count. At 0, say "teleX will sign out of this Telegram account and delete its session. To use it in teleX again, you'll link it from the start."
- T82 updates `screens.md:245`.

## RED first

Component (Vitest, `AccountsPage.test.tsx`): open the dialog for an account with 1 synced chat and expect "the 1 chat it synced"; with 0 expect no chat clause. Both fail today.

## Definition of Done

The SCR-60 unlink dialog reads "the 1 chat it synced" for one chat, "the N chats" for more, and has no count clause at zero; screens.md matches. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
