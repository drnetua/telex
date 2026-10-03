---
id: T7
title: "Add the connectivity state and the 3 s pulse query, and narrow fetch-client failure routing"
layer: "ui"
deps: []
blocks: ["T12", "T13"]
acs: ["AC-173", "AC-176", "AC-177"]
files_hint: ["frontend/src/shell/connectivity.ts", "frontend/src/shell/connectivity.test.ts", "frontend/src/shell/pulse.ts", "frontend/src/shell/pulse.test.tsx", "frontend/src/api/client.ts", "frontend/src/api/client.test.ts", "frontend/src/app/queryClient.ts", "frontend/src/app/FailureBoundary.tsx", "frontend/src/app/FailureBoundary.test.tsx"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T7 — Add the connectivity state and the 3 s pulse query, and narrow fetch-client failure routing

## Place in the sequence

- **Blocked by:** — (builds against `contracts/openapi.yaml` with mocked `fetch`) · **Blocks:** T12 — Show the live Inbox counter, T13 — Port the StatusBanner · **Wave:** 1.
- **Lane:** own lane (only task touching `api/client.ts` and `queryClient.ts`).

## Why (user story)

> **As an** Owner
> **I want** a Status Banner whenever something stops teleX from working for me, with one thing I can do about it
> **So that** I don't mistake a broken connection for an empty Inbox or a lost action
>
> — `spec.md §4, US-72, verbatim` · full text: [spec.md](../spec.md)

This task is the non-visual engine behind the banner and counter: one connectivity state, the pulse that feeds it, and the client that stops throwing the Owner off their screen.

## Inlined context

> **No global state library.** Server state stays in TanStack Query: `me` and `pulse`. The connectivity state is one small module the fetch client, the pulse and the banner share, and it drives TanStack's `onlineManager`. The pulse itself runs with `networkMode: 'always'`, so it keeps polling every 3 s while every other query is paused, and its first success brings the state back to online.
>
> — `sad.md §4, UI architecture, abridged` · decision: [adr/0004](../adr/0004-detect-offline-from-pulse-failures-and-browser-network-state.md)

> `connectivity.ts` — state online | offline | not-responding; browser events; drives onlineManager. `pulse.ts` — usePulse: background query every 3 s while visible, 2 s timeout, networkMode always (never paused by onlineManager — it is what detects recovery). `api/client.ts` — no-answer and network errors feed connectivity instead of SCR-93.
>
> — `sad.md §5, Internal decomposition (frontend), abridged` · full text: [sad.md](../sad.md)

> **Failure routing inside the shell (narrowed):** `401 unauthenticated` → SCR-01, with the section remembered (AC-173). `401 session-ended` → SCR-92. **No answer** (2 s for the pulse, 10 s for calls), a **network error**, or **any `502`/`503`/`504`, whatever its body** → the Status Banner, and the screen stays. Any other `5xx` (`500 internal-error`) and `403 forbidden` → SCR-93 with Retry. **Outside the shell (auth and system pages, unchanged from E01):** no answer within 10 s, any `5xx` or `403` → SCR-93.
>
> — `screens.md §Shared conventions, Failure routing, abridged` · full text: [screens.md](../screens.md)

> Seed flow 2: device offline → connectivity offline, queries pause · pulse/action with no answer, network error or 502/503/504 → not-responding, queries pause · Try again while down → pulse now, still no answer · every 3 s while down → pulse · pulse answered → online, paused queries refetch. The pulse fires immediately when the tab becomes visible.
>
> — `sad.md §6, Critical flow 2 + §11 iOS risk, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** E01 tests asserting the old AC-102 behavior will fail — update `client.test.ts` in the same task that changes the fetch client.
>
> — `sad.md §11, risk "AC-102 behavior changes", abridged`

**Fallback:** insufficient or contradicted by the code → read [adr/0004](../adr/0004-detect-offline-from-pulse-failures-and-browser-network-state.md) and [sad.md](../sad.md) §8 Connectivity. Do not guess.

## Data delta

No DB changes.

## API contract

- Calls `GET /api/v1/pulse` (`getPulse`) with `X-Telex-Background: 1`, 2 s timeout → `200 {inboxCount, conditions[]}`; `401 unauthenticated|session-ended` → existing failure bus routes. Unknown response fields ignored.

— `contracts/openapi.yaml, operationId getPulse, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-173 — authorization

> **Given** a visitor without a live Sign-in Session
> **When** they open a link to any section, including a "Coming soon" one, or take their next action in a tab that was open when the session stopped
> **Then** they see nothing of the shell (no sections, no counter, no banners): a person who never signed in or signed out sees the sign-in page, and a session that ran out or was revoked shows the "Session ended" page; when sign-in finishes in that same browser (Sign-in Code, Passkey, or a Sign-in Link opened there) they land on the section the link pointed to, otherwise on the Inbox, and a brand-new account first gets the passkey offer
>
> — `spec.md §5, AC-173, verbatim` · full text: [spec.md](../spec.md)

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

## Checklist

- [ ] `connectivity.ts`: store with `online | offline | not-responding`, `subscribe`, `reportNoAnswer()`, `reportAnswered()`, browser `online`/`offline` listeners, `onlineManager.setOnline` driven from it; `retryNow()` that fires one pulse and reports `stillDown` — `frontend/src/shell/connectivity.ts`
- [ ] `pulse.ts`: `usePulse()` — `refetchInterval: 3000` only while `document.visibilityState === 'visible'`, immediate refetch on `visibilitychange` to visible, `networkMode: 'always'`, 2 s timeout, background header; success → `reportAnswered()` — `frontend/src/shell/pulse.ts`
- [ ] `client.ts`: accept a per-call `timeoutMs`; classify no-answer / network error / 502·503·504 as a connectivity failure (`route: "connectivity"`) and report it — `frontend/src/api/client.ts`
- [ ] `FailureBoundary` + `queryClient`: inside the shell a connectivity failure keeps the screen (no SCR-93); outside the shell (auth/system layouts) it still routes to SCR-93 — `frontend/src/app/`
- [ ] Vitest: update `client.test.ts` / `FailureBoundary.test.tsx` for the narrowed AC-102; new `connectivity.test.ts`, `pulse.test.tsx` (fake timers)

## Edge cases

| Case | Behaviour |
|---|---|
| Pulse answers after 2 s | Counted as no answer → `not-responding` (sad §11 accepted) |
| `503` with a teleX problem body inside the shell | Banner, not SCR-93 (any body) |
| `500 internal-error` / `403` on an action inside the shell | SCR-93 with Retry |
| No answer on an auth page (SCR-01/07/08/09) | SCR-93, unchanged |
| Pulse `401 session-ended` | Pulse stops, cache cleared, SCR-92 |
| Tab hidden | Pulse stops; resumes at once when visible |
| Recovery | First answered pulse → `online`, paused queries refetch |

## Definition of Done

- [ ] Vitest for every edge case passes; updated E01 tests pass
- [ ] `pnpm run check` clean (tsc, ESLint, Prettier, Vitest)
- [ ] every Hard Rule inlined above still holds
