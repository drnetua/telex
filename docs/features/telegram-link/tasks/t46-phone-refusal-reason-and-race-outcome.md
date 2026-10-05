---
id: T46
title: "Say which phone refusal ended the attempt, and end a sign in again whose account was unlinked meanwhile as not found"
layer: "app"
deps: []
blocks: ["T48", "T50"]
acs: ["AC-107", "AC-117", "AC-04"]
files_hint: ["backend/app/src/main/kotlin/telex/messaging/internal/account/LinkCompletion.kt", "backend/app/src/main/kotlin/telex/messaging/Linking.kt", "backend/app/src/integrationTest/kotlin/telex/messaging/LinkingCompletionIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/LinkingApiIT.kt", "frontend/src/pages/connect-telegram/ConnectTelegramPage.tsx", "frontend/src/pages/connect-telegram/Outcomes.tsx", "frontend/src/pages/connect-telegram/PhoneStep.tsx", "frontend/src/pages/connect-telegram/steps.ts", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx", "frontend/src/pages/connect-telegram/Outcomes.test.tsx", "frontend/src/messages.ts", "docs/features/telegram-link/contracts/openapi.yaml"]
owner: "Anton Husiev"
estimate: "M"
source: "review 2026-10-04 (second pass) — findings A1, A2"
status: "done"
---

# T46 — Say which phone refusal ended the attempt, and end a sign in again whose account was unlinked meanwhile as not found

## Origin

Follow-up from the second-pass review: [`_review/review-2026-10-04-r2.md`](../_review/review-2026-10-04-r2.md), findings **A1, A2** (resolved "Fix now" by the user). Read those rows in the review record first — they carry the cited `file:line` and the failure scenario. The ACs below are the source of truth for what the tests assert: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-107, AC-117, AC-04
- **Blocked by:** — · **Blocks:** T48, T50

## What to change

- A1 (AC-107): since T41 a 422 `telegram-phone-unregistered|-invalid|-banned` on `/code` and `/code/resend` ends the attempt, but `ConnectTelegramPage.outcomeOf` maps `endsAttempt` to `{ kind: "ended" }` and the generic "This linking has ended — cancelled, finished elsewhere or left for 15 minutes" card shows a false reason. Carry the problem code into the outcome (e.g. `{ kind: "ended", reason }`) and render the plain-language refusal from `messages.linking.problems` (for unregistered: create the account in the Telegram app first) with "Start again". Same for the phone step's unregistered-with-no-replacement-session case (T41/N4): the SPA must know the attempt ended (the 422 at the phone step for that case now ends it) instead of staying on the step until a 404.
- A2 (AC-117, AC-04): when an unlink deletes the target during a targeted Sign in again, `LinkCompletion.signInAgain` returns `Refused(MISMATCH)` → 409 `telegram-account-mismatch`, whose card says "a different Telegram account, not ``" (untrue, empty name). End it instead as 404 `linking-attempt-not-found` (the attempt is discarded and its session logged out, as today), which shows the "ended" card. Keep the untargeted fall-through to `link()`. Record both race outcomes in `contracts/openapi.yaml` (the relevant response descriptions).

## RED first

IT: targeted race (spy deletes the row inside completion, as the existing test does) → 404 `linking-attempt-not-found`, session logged out + closed + directory gone, no CONNECTED event (update the existing test's expected status — the behaviour is changing by user decision, record that in the commit body). Vitest: a 422 `telegram-phone-unregistered` on code submit and on resend shows the unregistered text ('Telegram app first'), invalid and banned show their text; assert the copy, not only the heading.

## Definition of Done

The Owner always sees the real reason a phone refusal ended the attempt; the race never claims a different account; contract records both outcomes. Per-task gate clean (unit + integration + detekt/ktlint for backend; `pnpm run check` for frontend/e2e). No test weakened; tests that asserted the buggy behaviour are corrected, not deleted.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
