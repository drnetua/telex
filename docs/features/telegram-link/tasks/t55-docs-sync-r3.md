---
id: T55
title: "Sync sad \u00a77 tags, ux-flows, the sad coverage row and screens.md with the code, and record the third-review changes"
layer: "docs"
deps: ["T51", "T52", "T53", "T54"]
blocks: []
acs: []
files_hint: ["docs/features/telegram-link/sad.md", "docs/features/telegram-link/ux-flows.md", "docs/features/telegram-link/screens.md", "docs/features/telegram-link/contracts/openapi.yaml", "docs/features/telegram-link/spec.md", "docs/features/app-shell/screens.md"]
owner: "Anton Husiev"
estimate: "S"
source: "review 2026-10-04 (third pass) — findings D1–D5 (doc halves), plus the doc side of K1–K3 and W4"
status: "done"
---

# T55 — Sync sad §7 tags, ux-flows, the sad coverage row and screens.md with the code, and record the third-review changes

## Origin

Follow-up from the third-pass review: [`_review/review-2026-10-04-r3.md`](../_review/review-2026-10-04-r3.md), findings **D1–D5 (doc halves), plus the doc side of K1–K3 and W4** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** —
- **Blocked by:** T51, T52, T53, T54 · **Blocks:** —

## What to change

- D1: add `refused_target_gone` to the sad §7 `telex.linking.attempts{outcome=…}` tag list with a one-line meaning (`Linking.kt:331`).
- D2: ux-flows — add the "ended (refusal text)" edge from the code step and from Send a new code (and an unregistered number with no fresh session), and the US-51 race edge "account unlinked meanwhile → attempt ended → Start again as a plain add". Set the sad coverage row for AC-107 to "flow 5, flow 6 (resend)" (`sad.md:816`).
- D3: reword `screens.md:65` — the SPA checks the attempt at once and shows `attempt-ended` with the unregistered text.
- D4: `screens.md:39` — replace the removed `useLive` hook with "`useConditionLives` calls `useAccountDisconnected`".
- D5: correct `ux-flows.md:175` to name the tests that actually cover AC-110 (the new T54 Vitest case + backend ITs).
- Doc side of the code fixes: sad Flow for unlink (CF2) — teleX's own logout no longer passes through Session lost and is confirmed only when TDLib finishes it (T51); Flow 3/boot — an account unlinked during reopen is closed and destroyed (T52); Flow 6 resend refusals now come from the real adapter (T52). If T53 kept the 403 route inside the shell, confirm app-shell `screens.md:51` still matches; otherwise record the exception. Add to spec §8 / ship-time list: confirm on the real-account check that the teleX device is gone from Telegram's active sessions after an unlink.

## RED first

Docs only — no RED. Verify each changed line against the code it describes (cite file:line in the commit body).

## Definition of Done

sad, ux-flows, screens.md, the contract and spec §8 describe the code as built after T51–T54. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
