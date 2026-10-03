---
id: T18
title: "e2e: live Inbox counter and Status Banners, with timing, and the narrowed AC-102"
layer: "tests"
deps: ["T6", "T12", "T13", "T17"]
blocks: []
acs: ["AC-174", "AC-175", "AC-176", "AC-177", "AC-178"]
files_hint: ["e2e/tests/live-signals.spec.ts", "e2e/tests/system-pages.spec.ts", "e2e/support/shell.ts"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T18 — e2e: live Inbox counter and Status Banners, with timing, and the narrowed AC-102

## Place in the sequence

- **Blocked by:** T6 — pulse + fixtures, T12 — live Inbox counter, T13 — StatusBanner, T17 — e2e harness and `e2e` profile · **Blocks:** — · **Wave:** 6.
- **Lane:** shares `e2e/support/shell.ts` with T17 and T19 — serialized.

## Why (user story)

> **As an** Owner
> **I want** a Status Banner whenever something stops teleX from working for me, with one thing I can do about it
> **So that** I don't mistake a broken connection for an empty Inbox or a lost action
>
> — `spec.md §4, US-72, verbatim` · full text: [spec.md](../spec.md)

This task proves the live signals within their 5 s budgets on both widths.

## Inlined context

> | Offline Status Banner appears | ≤ 5 s after the device loses its network or teleX stops answering |
> | Offline Status Banner clears | ≤ 5 s after the connection returns |
> | Inbox counter freshness | ≤ 5 s from an item landing or being resolved to the new number on screen |
>
> — `spec.md §6, NFR rows, abridged` · full text: [spec.md](../spec.md)

> QG-1a: cut the network with `context.setOffline(true)`, and separately stop answers by routing `/api/**` to abort, then measure the time until the banner is visible. QG-1b: restore and measure until the banner is gone and a fresh pulse has landed. QG-1c: under the `e2e` profile, change the count through the fixture `InboxSource` and measure until the number on screen matches, on both profiles, including the 0 (no number) and "99+" cases.
>
> — `sad.md §10, QG-1a…1c, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** E01's AC-102 behavior changes: an action with no answer in 10 s no longer opens SCR-93. Update `e2e/tests/system-pages.spec.ts`. SCR-93 inside the shell now only for `500` (other 5xx except 502/503/504) or `403`.
>
> — `sad.md §11 + screens.md §SCR-93, abridged`

> Banner texts: "You're offline. teleX will update when your connection is back." · "teleX isn't responding." · still down: "Still can't reach teleX. It keeps trying on its own." · several: "+{n} more".
>
> — `screens.md §Shell rows banner-*, abridged`

**Fallback:** read [sad.md](../sad.md) §10 and [screens.md](../screens.md) §Shell. Do not guess.

## Data delta

No DB changes.

## API contract

- `PUT /api/v1/e2e-fixtures/pulse` (`setPulseFixture`) body `{inboxCount, conditions}` → `204`; affects only the calling Owner.

— `contracts/openapi.yaml, operationId setPulseFixture, abridged`

## Acceptance criteria

### AC-174 — cross-context

> **Given** a signed-in Owner whose Inbox holds 3 waiting items, with teleX open on any section
> **When** a new item lands in their Inbox, or one is resolved in another browser of the same Owner
> **Then** the Inbox counter on the current screen changes to 4 (or 2) without a page reload; when no items wait, the Inbox item shows no number but stays in place, and above 99 it shows "99+". An item counts while the epic that put it in the Inbox says it waits for the Owner; E06 adds no kind of item itself, so until E11 the Inbox stays empty with its "Connect Telegram" step
>
> — `spec.md §5, AC-174, verbatim` · full text: [spec.md](../spec.md)

### AC-175 — authorization

> **Given** two Owners, one with 5 waiting Inbox items and one with none
> **When** the Owner with none opens teleX
> **Then** their Inbox counter shows no number, and nothing in their shell reveals how many items any other Owner has
>
> — `spec.md §5, AC-175, verbatim`

### AC-176 — happy

> **Given** a signed-in Owner on any screen of the shell
> **When** their device has no network, or it has one but teleX doesn't answer (for example while teleX restarts)
> **Then** a Status Banner under the header says so ("You're offline" or "teleX isn't responding"), with an icon and words (not color alone) and a "Try again" action; when the connection comes back, the banner disappears by itself and the current screen shows fresh data; the full-page "teleX is unavailable" from E01 now appears only when teleX answers an action with a failure
>
> — `spec.md §5, AC-176, verbatim`

### AC-177 — error

> **Given** an Owner who sees the offline Status Banner
> **When** they press "Try again" while the connection is still down
> **Then** the banner stays, tells them teleX is still unreachable and that it keeps retrying on its own, and nothing they were viewing is cleared from the screen
>
> — `spec.md §5, AC-177, verbatim`

### AC-178 — domain invariant

> **Given** an Owner on a screen where one or more Status Banner conditions hold
> **When** they look for a way to close a banner, or a second condition starts while the first still holds
> **Then** no banner can be dismissed while its cause remains ("a banner lives exactly as long as its cause"), and when several conditions hold only the most important banner shows, chosen by a fixed importance order (§8), with "N more" that lists the others, each with its own action
>
> — `spec.md §5, AC-178, verbatim`

## Checklist

- [ ] Counter: fixture 3 → 4 → 2 within 5 s without reload; 0 → no number; 100 → "99+"; second Owner (own browser context) sees no number while the first has 5 — `e2e/tests/live-signals.spec.ts`
- [ ] Banners: `setOffline(true)` → offline banner ≤ 5 s; route `/api/**` abort → not-responding ≤ 5 s; Try again while down → still-down text, page content intact; restore → banner gone ≤ 5 s; no close control
- [ ] Several: fixture `["account-disconnected", "budget-exhausted"]` + offline → offline shown, "+2 more" lists both with their actions
- [ ] Update `e2e/tests/system-pages.spec.ts`: no-answer action inside the shell keeps the screen + banner; answered `500` still shows SCR-93

## Edge cases

| Case | Behaviour |
|---|---|
| Fixture conditions only, no connectivity issue | Most important fixture condition shows with "+N more" |
| `503` from the proxy route | Not-responding banner, not SCR-93 |
| Counter while offline | Last number kept |

## Definition of Done

- [ ] Specs green under both Playwright projects in CI, timings asserted ≤ 5 s
- [ ] `pnpm --filter @telex/e2e run check` clean
