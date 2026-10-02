---
id: T13
title: "Set up the SPA router, query client and fetch client with failure routing, and build the system pages SCR-91, SCR-92 and SCR-93"
layer: "ui"
deps: []
blocks: ["T14", "T17"]
acs: ["AC-101", "AC-102", "AC-96", "AC-93"]
files_hint: ["frontend/package.json", "frontend/vite.config.ts", "frontend/src/main.tsx", "frontend/src/App.tsx", "frontend/src/app/", "frontend/src/api/", "frontend/src/pages/system/", "frontend/src/components/Button/", "frontend/src/components/EmptyState/", "frontend/src/components/Icon/", "frontend/src/messages.ts", "pnpm-lock.yaml"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T13 — Set up the SPA router, query client and fetch client with failure routing, and build the system pages SCR-91, SCR-92 and SCR-93

## Place in the sequence

- **Blocked by:** — · **Blocks:** T14 — Build Sign in (SCR-01, email part) and Check your email (SCR-07) with the Sign-in Code, T17 — Build the signed-in PageFrame with Sign out and the empty Inbox (SCR-10) · **Wave:** 1 — builds against the contract only; starts in parallel with T1–T3.
- **Lane:** owns `frontend/src/app/` and `frontend/src/api/`; every later `ui` task depends on it.

## Why (user story)

> **As an** Owner
> **I want** a clear system page when a page doesn't exist, my session ended, or teleX can't be reached
> **So that** I always know what happened and have one action to get back
>
> — `spec.md §4, US-49, verbatim` · full text: [spec.md](../spec.md)

This task gives the SPA its spine — routes, server state and one fetch client — and the three system pages every failure lands on.

## Inlined context

> **UI architecture (web-frontend): a client-side SPA served as static files by the app.** […] This feature adds:
> - **React Router** for the SCR routes;
> - **TanStack Query** for server state, as planned in `architecture-map.md` §Frontend;
> - one fetch client that owns the 10-second timeout, the problem-code → system-page mapping (sad §8) and the background-request marker (ADR-0005).
>
> There is no global state library; session state is a TanStack query on "who am I".
>
> — `sad.md §4, UI architecture, abridged` · full text: [sad.md](../sad.md)

> **SPA failure handling:** One fetch client: `unauthenticated` → SCR-01 with the current path remembered; `session-ended` → SCR-92; no answer within 10 s or a 5xx → SCR-93 whose "Retry" repeats the failed request; unknown client route → SCR-91 (AC-101, AC-102). Sign-out clears the TanStack cache so Back shows no data (AC-95)
> **Remembered destination:** Stored in `localStorage` of the browser that asked to sign in; accepted only if it is a relative path starting with a single `/`, otherwise Inbox. […]
>
> — `sad.md §8, SPA failure handling + Remembered destination, abridged` · full text: [sad.md](../sad.md)

> The marker is set in one place, the fetch client's TanStack integration, not at each call site.
>
> — `adr/0005 §Decision outcome, abridged` · full text: [adr/0005](../adr/0005-count-session-activity-only-from-requests-the-spa-does-not-mark-as-background.md)

> **Failure routing:** `401 unauthenticated` → SCR-01, with the current path remembered (AC-101). `401 session-ended` → SCR-92 (AC-93, AC-96). No answer within 10 s, any `5xx` (including `503 mail-unavailable`), or `403 forbidden` → SCR-93, whose Retry repeats the request (AC-102). […] **Bare system layout.** SCR-91, SCR-92 and SCR-93 use the same centered card, but with the **text wordmark "teleX"** in place of the logo image […]. They never depend on a session or on `/api/v1/me`. **Busy button.** […] `disabled` with a Tabler `spinner-border-sm` and keeps its label […] announced politely.
>
> — `screens.md §Shared conventions, abridged` · full text: [screens.md](../screens.md)

**Screens and states to build (screens.md):**
- **SCR-91** `default` (h1 "Page not found", `EmptyState kind="none"` icon `search`, "This page doesn't exist in teleX.", action "Go to Inbox" → `/inbox`) · `signed-out` (same render).
- **SCR-92** `default` (h1 "Session ended", `EmptyState kind="blocked"` icon `logout`, "Your session has ended. Sign in again to continue.", "Sign in again" → `/sign-in`; cache cleared on entry).
- **SCR-93** `default` (h1 "teleX is unavailable", `EmptyState kind="blocked"` icon `wifi-off`, "teleX didn't answer. Check your connection, then try again.", `Button` primary `icon="refresh"` "Retry") · `retrying` (busy "Retrying") · `retry-failed` (+ `small` "Still no answer." in `aria-live="polite"`) · `success` (back to the originating page).

— `screens.md §SCR-91, §SCR-92, §SCR-93, abridged` · full text: [screens.md](../screens.md)

**Route table (set by this breakdown; T5/T7 email links depend on two of them):** `/sign-in` SCR-01 · `/sign-in/check-email` SCR-07 · `/sign-in/link` SCR-08 (token in `#fragment`) · `/welcome/passkey` SCR-09 · `/inbox` SCR-10 (`/` redirects here) · `/profile` SCR-64 (`#sessions` anchor) · `/session-ended` SCR-92 · `*` SCR-91. SCR-93 renders in place of the page content (no route), so Retry can repeat the exact request.

**Reuse:** port `Button` (+ new `busy` prop, screens.md §New components), `EmptyState`, `Icon` from `docs/docs/design-system/components/` (typed in `components/index.d.ts`) into `frontend/src/components/`; Tabler `page-center`, `card`; tokens only.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Consumes the cross-cutting contract (no operation of its own):

- Request headers: `X-XSRF-TOKEN` = value of the `XSRF-TOKEN` cookie on every POST/DELETE; `X-Telex-Background: 1` on TanStack refetches (focus/interval/reconnect), never on first loads or mutations.
- Problems: `application/problem+json` with `code` — `unauthenticated`, `session-ended` (401) · `forbidden` (403) · `internal-error`, `mail-unavailable` (5xx) are routed here; everything else goes back to the caller.

— `contracts/openapi.yaml, info.description + parameters XsrfToken/Background + responses NotSignedIn/Forbidden, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-101 — error

> **Given** a signed-out person who opens a link to a teleX page
> **When** the page needs a Sign-in Session
> **Then** they see the sign-in page, and after signing in they land on the page they originally opened, but only if it is a teleX page; any other destination leads to the Inbox
>
> — `spec.md §5, AC-101, verbatim` · full text: [spec.md](../spec.md)

### AC-102 — error

> **Given** a signed-in Owner
> **When** they open an address that doesn't exist in teleX, or teleX stops responding while they use it (an action gets no answer within 10 seconds, or teleX answers with a server failure)
> **Then** they see the matching system page ("Page not found" with a "Go to Inbox" action, or "teleX is unavailable" with a "Retry" action that repeats the action); "teleX is unavailable" can only appear in a teleX tab that has already loaded, and opening teleX from scratch while it is down shows the browser's own error
>
> — `spec.md §5, AC-102, verbatim` · full text: [spec.md](../spec.md)

### AC-96 — domain invariant

> **Given** a Sign-in Session that has been idle for 30 days (activity means a page the Owner opens or an action they take; background refreshes of an open tab don't count), or that started 90 days ago no matter how active it is
> **When** the Owner next opens teleX in that browser
> **Then** they see the "Session ended" page with a "Sign in again" action
>
> — `spec.md §5, AC-96, verbatim` · full text: [spec.md](../spec.md)

### AC-93 — happy

> **Given** an Owner signed in on a laptop and a phone
> **When** they open Profile and security on the laptop and end the phone's session
> **Then** the list shows each session's browser, device type and last activity, with the current one marked "This device", and the phone's next action lands on the "Session ended" page
>
> — `spec.md §5, AC-93, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Add `react-router` and `@tanstack/react-query` to `frontend/package.json` (pnpm).
- [ ] `frontend/src/app/` — router with the route table, `QueryClientProvider`, auth-layout + bare-system-layout shells; placeholder elements for routes later tasks fill.
- [ ] `frontend/src/api/` — `apiFetch` (10 s `AbortController`, CSRF header, background marker via a query-meta/`queryFn` context flag, problem parsing), a failure router that remembers `location.pathname + search + hash` in `localStorage` under one key, and `takeRememberedDestination()` (single-slash rule, cleared on read).
- [ ] SCR-93 as an in-place failure boundary holding the failed request for Retry.
- [ ] `frontend/src/pages/system/` — SCR-91, SCR-92 (clears the query cache on entry), SCR-93.
- [ ] Port `Button` (with `busy`), `EmptyState`, `Icon` into `frontend/src/components/`.
- [ ] `vite.config.ts` proxy adds `/webauthn` and `/login/webauthn` (ADR-0006 dev loop).
- [ ] Strings in `frontend/src/messages.ts`; replace the scaffold `App` home copy.
- [ ] Vitest next to each module.

## Edge cases

| Case | Behaviour |
|---|---|
| Remembered `//evil.example/x` or `https://…` | rejected → `/inbox` |
| Remembered `/profile#sessions` | accepted as is |
| Request answers after 10 s | aborted → SCR-93; Retry repeats it once per click |
| Retry fails again | stays on SCR-93 with "Still no answer." |
| `403 forbidden` (stale CSRF) | SCR-93; Retry re-reads the `XSRF-TOKEN` cookie (screens.md §Noted gaps 2) |
| Cold open while the server is down | browser's own error — nothing to build (AC-102) |

## Definition of Done

- [ ] Vitest covers the fetch client, the remembered-destination rule and every SCR-91/92/93 state.
- [ ] Pages work at 360 px and 1280 px; status never by color alone; no raw hex.
- [ ] `pnpm run check` clean (tsc, ESLint, Prettier, Vitest).
- [ ] every Hard Rule inlined above still holds.
