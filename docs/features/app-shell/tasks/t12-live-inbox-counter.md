---
id: T12
title: "Show the live Inbox counter in the shell from the pulse (none at 0, 99+ above 99)"
layer: "ui"
deps: ["T7", "T10"]
blocks: ["T18"]
acs: ["AC-174", "AC-175"]
files_hint: ["frontend/src/shell/AppShell/", "frontend/src/shell/InboxCounter.tsx", "frontend/src/shell/InboxCounter.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "S"
status: "todo"
---

# T12 — Show the live Inbox counter in the shell from the pulse (none at 0, 99+ above 99)

## Place in the sequence

- **Blocked by:** T7 — Connectivity state and pulse query, T10 — Port AppShell · **Blocks:** T18 — e2e: Inbox counter and Status Banners · **Wave:** 4.
- **Lane:** shares `shell/AppShell/` with T13 — serialized by `implement`.

## Why (user story)

> **As an** Owner
> **I want** the Inbox counter in the navigation to always show how many items wait for me
> **So that** I notice new work without opening the Inbox
>
> — `spec.md §4, US-71, verbatim` · full text: [spec.md](../spec.md)

This task mounts the pulse in the shell and turns its count into the Inbox badge on both widths.

## Inlined context

> Seed flow 1 (client side): pulse answered → update the counter (no number at 0, 99+ above 99) → counter changes on the current screen, no reload.
>
> — `sad.md §6, Critical flow 1, abridged` · full text: [sad.md](../sad.md)

> `counter-none`: `Pulse.inboxCount = 0`, or before the first pulse answers → Inbox item with no number; the item stays in place. `counter`: 1…99 → `tx-count` pill (Tabler `badge bg-primary`, tokens only) with the number and `aria-label` "{n} items need you". A change is announced politely once per change, not every pulse. `counter-max`: > 99 → pill "99+", `aria-label` "More than 99 items need you". Phone: counter pill on the Inbox icon in the bottom bar.
>
> — `screens.md §Shell rows counter-none, counter, counter-max + W-S2, abridged` · full text: [screens.md](../screens.md)

> NFR Inbox counter freshness: ≤ 5 s from an item landing or being resolved to the new number on screen.
>
> — `spec.md §6, verbatim`

**Fallback:** read [screens.md](../screens.md) §Shell and [adr/0002](../adr/0002-poll-one-background-pulse-every-3-seconds-for-live-signals.md). Do not guess.

## Data delta

No DB changes.

## API contract

- Consumes `usePulse()` (T7) → `Pulse.inboxCount: int ≥ 0`, uncapped; SPA caps the display.

— `contracts/openapi.yaml, schema Pulse, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

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
> — `spec.md §5, AC-175, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `InboxCounter` (pill + accessible label + polite live region that speaks only on change) — `frontend/src/shell/InboxCounter.tsx`
- [ ] Mount `usePulse()` once in `AppShell`; pass `inboxCount` to the Inbox item in side menu and bottom bar — `frontend/src/shell/AppShell/`
- [ ] Copy into `frontend/src/messages.ts`
- [ ] Vitest: 0 → no number; 3 → "3"; 3 → 4 updates without remount; 100 → "99+" with its label; same value twice → one announcement

## Edge cases

| Case | Behaviour |
|---|---|
| Before the first pulse answers | No number |
| `inboxCount` 0 | No number; Inbox item stays |
| 99 / 100 | "99" / "99+" |
| Pulse failing (offline) | Last known number kept; banner (T13) explains |

## Definition of Done

- [ ] Vitest for every edge case passes
- [ ] `pnpm run check` clean; pill uses tokens only and is not the only status cue (label + aria-label)
