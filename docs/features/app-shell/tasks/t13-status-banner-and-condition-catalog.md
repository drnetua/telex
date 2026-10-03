---
id: T13
title: "Port StatusBanner (C-04) with the condition catalog, Try again, still-down and N more"
layer: "ui"
deps: ["T7", "T10"]
blocks: ["T17", "T18"]
acs: ["AC-176", "AC-177", "AC-178"]
files_hint: ["frontend/src/shell/StatusBanner/", "frontend/src/shell/conditions.ts", "frontend/src/shell/conditions.test.ts", "frontend/src/shell/AppShell/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T13 — Port StatusBanner (C-04) with the condition catalog, Try again, still-down and N more

## Place in the sequence

- **Blocked by:** T7 — Connectivity state and pulse query, T10 — Port AppShell · **Blocks:** T17 — e2e: the shell sweep (banner screen), T18 — e2e: Inbox counter and Status Banners · **Wave:** 4.
- **Lane:** shares `shell/AppShell/` with T12 — serialized by `implement`.

## Why (user story)

> **As an** Owner
> **I want** a Status Banner whenever something stops teleX from working for me, with one thing I can do about it
> **So that** I don't mistake a broken connection for an empty Inbox or a lost action
>
> — `spec.md §4, US-72, verbatim` · full text: [spec.md](../spec.md)

This task shows the connectivity state and every server-reported condition as one banner strip with a fixed priority.

## Inlined context

> **Two extension points keep the shell closed to change.** Status Banner conditions come from `StatusConditionSource` implementations reported through the pulse, and a client catalog maps each code to its text, action and fixed importance.
> **Status Banner** concept: conditions come from the client (offline, not-responding) and from `StatusConditionSource` codes in the pulse. The most important condition shows, the rest are listed under "N more", and none can be closed. An unknown code is logged once by the SPA console and ignored.
>
> — `sad.md §4 choice 5 + §8 Status Banner + Logging, abridged` · decision: [adr/0006](../adr/0006-extend-the-shell-through-client-section-and-server-condition-registries.md)

> Importance order (user, 2026-10-03): **offline / not responding > account disconnected > bot blocked > consent needed > budget exhausted > all assistants paused > triage deferred**.
>
> — `screens.md §Source, Spec §8 OQs resolved, verbatim` · full text: [screens.md](../screens.md)

> `banner-offline`: under the header (phone) or top of main column (desktop): `warning-subtle`, `Icon wifi-off`, "You're offline. teleX will update when your connection is back." + `Button` secondary sm "Try again". `role="status"`, no close control. · `banner-not-responding`: `Icon cloud-off`, "teleX isn't responding." + "Try again". · `banner-retrying`: one immediate pulse; "Try again" `Button busy` ("Trying again"). · `banner-still-down`: text replaced by "Still can't reach teleX. It keeps trying on its own." (announced politely); content kept. · `banner-several`: most important shows, plus `Button` link "+{n} more" (`aria-expanded`) expanding an inline list; each row icon, words, own action. · `banner-unknown-code`: not shown, logged once. · `recovered`: banner disappears without a message, paused queries refetch.
>
> — `screens.md §Shell rows banner-*, recovered, abridged` · full text: [screens.md](../screens.md)

> **Hard rule:** UI epics later add only `conditions.ts` entries (QG-3a). Status never by color alone; NFR: banner appears ≤ 5 s after loss, clears ≤ 5 s after return.
>
> — `sad.md §10 QG-3a + spec.md §6, abridged`

**Fallback:** port from `docs/docs/design-system/components/StatusBanner/` with a catalog-driven `conditions` prop; read [screens.md](../screens.md) W-S3, W-S4. Do not guess.

## Data delta

No DB changes.

## API contract

- Consumes `usePulse()` (T7) → `Pulse.conditions: StatusConditionCode[]` (open set, unordered); `offline` / `not-responding` never appear in it — they come from `connectivity.ts`.

— `contracts/openapi.yaml, schema StatusConditionCode, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-176 — happy

> **Given** a signed-in Owner on any screen of the shell
> **When** their device has no network, or it has one but teleX doesn't answer (for example while teleX restarts)
> **Then** a Status Banner under the header says so ("You're offline" or "teleX isn't responding"), with an icon and words (not color alone) and a "Try again" action; when the connection comes back, the banner disappears by itself and the current screen shows fresh data; the full-page "teleX is unavailable" from E01 now appears only when teleX answers an action with a failure
>
> — `spec.md §5, AC-176, verbatim` · full text: [spec.md](../spec.md)

### AC-177 — error

> **Given** an Owner who sees the offline Status Banner
> **When** they press "Try again" while the connection is still down
> **Then** the banner stays, tells them teleX is still unreachable and that it keeps retrying on its own, and nothing they were viewing is cleared from the screen
>
> — `spec.md §5, AC-177, verbatim` · full text: [spec.md](../spec.md)

### AC-178 — domain invariant

> **Given** an Owner on a screen where one or more Status Banner conditions hold
> **When** they look for a way to close a banner, or a second condition starts while the first still holds
> **Then** no banner can be dismissed while its cause remains ("a banner lives exactly as long as its cause"), and when several conditions hold only the most important banner shows, chosen by a fixed importance order (§8), with "N more" that lists the others, each with its own action
>
> — `spec.md §5, AC-178, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `conditions.ts`: catalog `code → {icon, messageKey, action, importance}` with `offline`, `not-responding` and the six later codes in the fixed order (E06 renders fixture codes in e2e); `orderConditions(codes)` drops and logs unknown codes once — `frontend/src/shell/conditions.ts`
- [ ] `StatusBanner` port: no close control, Try again → `connectivity.retryNow()` with busy and still-down states, "+N more" inline list — `frontend/src/shell/StatusBanner/`
- [ ] Mount in the `AppShell` banner slot, combining connectivity state + `pulse.conditions` — `frontend/src/shell/AppShell/`
- [ ] Add icons `cloud-off`, `wifi-off` if missing; copy into `frontend/src/messages.ts`
- [ ] Vitest: ordering; unknown code ignored + logged once; no close button; Try again busy → still-down; recovery hides it; two conditions → one banner + "+1 more" with its own action

## Edge cases

| Case | Behaviour |
|---|---|
| Offline and `account-disconnected` both hold | Offline shows; "+1 more" lists Account disconnected with its action |
| Unknown code `foo-bar` | Not shown; one console log |
| Try again while still down | Banner stays, text "Still can't reach teleX. It keeps trying on its own."; content kept |
| Connection returns | Banner disappears without a message |

## Definition of Done

- [ ] Vitest for every edge case passes
- [ ] `pnpm run check` clean; banner pairs icon + words, no close control
- [ ] `StatusBanner` inventory row in `docs/design-system.md` flipped to `frontend/src/shell/StatusBanner/`
