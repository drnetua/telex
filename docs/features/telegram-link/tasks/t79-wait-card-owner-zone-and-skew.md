---
id: T79
title: "The SCR-02 wait card shows the retry time in the Owner's zone and counts down from Retry-After"
layer: "ui"
deps: []
blocks: ["T82"]
acs: ["AC-02"]
files_hint: ["frontend/src/pages/connect-telegram/Outcomes.tsx", "frontend/src/api/client.ts", "frontend/src/api/client.test.ts", "frontend/src/pages/connect-telegram/ConnectTelegramPage.test.tsx"]
owner: "Anton Husiev"
estimate: "XS"
source: "review 2026-10-05 (ninth pass) — W15, W16"
status: "todo"
---

# T79 — The SCR-02 wait card shows the retry time in the Owner's zone and counts down from Retry-After

## Origin

Follow-up from the ninth-pass review: [`_review/review-2026-10-05.md`](../_review/review-2026-10-05.md), W15, W16 (resolved "Fix now" by the user). Read those rows first. The ACs below are the source of truth: read them verbatim in [spec.md §5](../spec.md).

- **ACs:** AC-02
- **Blocked by:** — · **Blocks:** T82

## What to change

- W15: `clock()` (`Outcomes.tsx:15-18`) formats `retryAt` with `getHours()/getMinutes()`, the device zone. Use `formatInstant` (`shell/time.ts`) with the Owner's zone from `useMe` (`api/account.ts`), 24-hour `HH:mm`.
- W16: `secondsUntil` (`Outcomes.tsx:11-13`) subtracts the browser clock from the server's `retryAt`. `client.ts:97-108` drops the `Retry-After` header the contract sends (`openapi.yaml:637-640`). Carry `Retry-After` in `ProblemExtras`, record when the answer arrived (monotonic `performance.now()` or `Date.now()` at receipt), and count down from that plus those seconds. Fall back to `retryAt` if the header is missing. Keep `retryAt` for the displayed time only.

## RED first

Component (Vitest):
- W15: the Owner's zone is `Asia/Tokyo` and the test runs in UTC. The card shows the Tokyo HH:mm of `retryAt`. Today it shows the UTC time.
- W16: a 429 with `Retry-After: 120` whose `retryAt` is 10 minutes off the faked device clock. The card counts down from 2:00, and shows "You can try again now" after 120 s. Today it shows the skewed value.
- Unit (`client.test.ts`): a 429 problem carries `retryAfterSeconds` from the header.

## Definition of Done

The wait card's HH:mm uses the Owner's saved time zone, and its countdown runs from when the answer arrived plus Retry-After, whatever the device clock says. Per-task gate clean. No test weakened.

**Fallback:** [spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) · [openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)
