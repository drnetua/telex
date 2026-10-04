---
id: T69
title: "Sync sad, test-plan, ux-flows, task statuses and T53's AC with the sixth-review fixes"
layer: "docs"
deps: ["T66", "T67", "T68"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/sad.md", "docs/features/telegram-link/test-plan.md", "docs/features/telegram-link/ux-flows.md", "docs/features/telegram-link/tasks.json", "docs/features/telegram-link/tasks/tracker.md"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-04 (sixth pass) — findings D12, D13, D14, D15, D16"
status: "done"
---

# T69 — Sync sad, test-plan, ux-flows, task statuses and T53's AC with the sixth-review fixes

## Origin

Follow-up from the sixth-pass review: [`_review/review-2026-10-04-r6.md`](../_review/review-2026-10-04-r6.md), findings **D12, D13, D14, D15, D16** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** —
- **Blocked by:** T66, T67, T68 · **Blocks:** —

## What to change

- D12: the flow 3 note (`sad.md:367`) says the boot reopen of an unlinked account logs it out "so … no teleX device stays in Telegram". Since T63 a reopen of a directory the unlink destroyed can't sign out. Reword: it tries to log it out (up to 10 s); if the unlink already destroyed the directory, the log out can't succeed and the Owner already got `signOutConfirmed=false`. Move the empty-database note (`sad.md:328`, inside flow 2's delete-fails branch) into flow 3.
- D13: `t01`–`t25` say `status: "todo"`; the tracker says `done`. Set them `done`.
- D14: `test-plan.md:118-120` edge cases: add an unopenable sealed key (deleted, sign-out unconfirmed), a failed delete after a confirmed sign-out (Session lost), a failed delete after an unconfirmed sign-out (session stays open and reporting), and a boot reopen on a destroyed directory (can't sign out).
- D15: `ux-flows.md:22` focus rule: add the banner's Sign in again replacing a card, and that focus never moves on the first load or between steps.
- D16: T53 claims `AC-176` (app-shell's). Write it as `app-shell/AC-176` in `tasks.json` and the T53 frontmatter.
- Mark T66–T69 `done`; update the tracker total.

## RED first

Docs only, no test.

## Definition of Done

sad flow 3 says the boot-side sign-out can fail on a destroyed directory and holds the empty-database note; t01-t25 frontmatter matches the tracker; test-plan lists the four unlink failure paths; the ux-flows focus rule matches screens.md; T53's foreign AC is qualified as app-shell/AC-176. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
