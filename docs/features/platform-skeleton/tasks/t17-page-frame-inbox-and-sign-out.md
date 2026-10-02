---
id: T17
title: "Build the signed-in PageFrame with Sign out and the empty Inbox (SCR-10)"
layer: "ui"
deps: ["T13"]
blocks: ["T18"]
acs: ["AC-100", "AC-95"]
files_hint: ["frontend/src/components/PageFrame/", "frontend/src/components/Toast/", "frontend/src/pages/inbox/", "frontend/src/api/account.ts", "frontend/src/messages.ts"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T17 — Build the signed-in PageFrame with Sign out and the empty Inbox (SCR-10)

## Place in the sequence

- **Blocked by:** T13 — Set up the SPA router, query client and fetch client with failure routing, and build the system pages SCR-91, SCR-92 and SCR-93 · **Blocks:** T18 — Build Profile and security (SCR-64): passkeys card and sign-in sessions card · **Wave:** 2 — needs the router, query client and fetch client (T13); runs in parallel with the sign-in pages.
- **Lane:** own lane; T18 reuses `PageFrame` (depends on this task).

## Why (user story)

> **As an** Owner
> **I want** to sign up and sign in with just my email address, using the link or the code from the email
> **So that** I never create or remember a password, on whichever device I read my email
>
> — `spec.md §4, US-01, verbatim` · full text: [spec.md](../spec.md)

This task builds where every sign-in ends — the empty Inbox with its one step — and the frame that holds Sign out.

## Inlined context

**SCR-10 states:**
- `loading` — `GET /api/v1/me` in flight: `PageFrame` + `LoadState state="loading"`.
- `default (empty)` — `Me.linkedAccountCount = 0` (always before E02): `PageFrame`, h1 "Inbox", `EmptyState kind="first"` (icon `brand-telegram`) "Connect your Telegram account to start.", action "Connect Telegram".
- `note` — "Connect Telegram" chosen (spec §8 OQ default): `Toast` kind info "Telegram linking is coming next." (dismisses itself, announced politely; choosing again shows it again).
- `with-account` — N/A before E02. `error` — shared failure routing.

**PageFrame (NEW):** Tabler `navbar` with the text wordmark "teleX" (→ SCR-10) left; `Button` ghost `icon="user"` "Profile and security" and `Button` ghost `icon="logout"` "Sign out" right. On phone: icon-only with `ariaLabel`, 44 px targets. Sign out is busy while it runs; failure → SCR-93 and the session stays live. **Temporary: E06 replaces it with `AppShell`.**

— `screens.md §SCR-10 + §New components, abridged` · full text: [screens.md](../screens.md) · wireframe W-10a; pattern source `docs/teleX-screens/Empty.html`

> Flow US-46 sign out: sign out → *no answer within 10 seconds, or a server failure* → SCR-93 teleX is unavailable with Retry, the session is still live → *signed out* → clear the session cookie → drop every cached piece of Owner data → SCR-01 Sign in → presses Back → Owner responses are never stored by the browser cache → ask for the earlier page's data, without a session cookie → unauthenticated → SCR-01 Sign in, no Owner data shown (AC-95).
>
> — `sad.md §6, Flow US-46 sign out, abridged` · full text: [sad.md](../sad.md)

**Reuse:** T13 `Button`, `EmptyState`, `Icon`, `LoadState` (from T15 if landed, else port here); port `Toast` from `docs/docs/design-system/components/Toast/`. `PageFrame` is new (`ux-flows.md`: no navigation shell in E01); register it as pending in the design-system notes.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

- `getMe` `GET /api/v1/me` → `200 {ownerId, email, linkedAccountCount}` · `401` → shared routing. This query is the SPA's session state.
- `signOut` `POST /api/v1/sign-out` → `204` (idempotent, clears `telex_session`) · `403` → SCR-93.

— `contracts/openapi.yaml, operationIds getMe + signOut, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-100 — cross-context

> **Given** a signed-in Owner who has no Linked Account
> **When** they open the Inbox
> **Then** they see the empty Inbox with the single step "Connect Telegram"; the step stays until the Owner has at least one Linked Account
>
> — `spec.md §5, AC-100, verbatim` · full text: [spec.md](../spec.md)

### AC-95 — happy

> **Given** a signed-in Owner
> **When** they choose "Sign out"
> **Then** they land on the sign-in page, and going back in the browser does not show any of their data
>
> — `spec.md §5, AC-95, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `frontend/src/api/account.ts` — `useMe()` query, `signOut()` mutation (on success: `queryClient.clear()`, navigate `/sign-in` with `replace`).
- [ ] `frontend/src/components/PageFrame/` (new) and `frontend/src/components/Toast/` (port).
- [ ] `frontend/src/pages/inbox/` — SCR-10 states.
- [ ] Copy in `messages.ts`; Vitest.

## Edge cases

| Case | Behaviour |
|---|---|
| Sign out times out | SCR-93; cache kept; session live |
| Back after sign out | no cached data rendered; the refetch gets `401` → SCR-01 (AC-95) |
| "Connect Telegram" twice | the toast shows again |
| Phone width | icon-only frame buttons with `ariaLabel`, 44 px targets |

## Definition of Done

- [ ] Vitest covers every SCR-10 state and both sign-out outcomes.
- [ ] Works at 360 px and 1280 px; tokens only; `ai` purple unused; copy in `messages.ts`.
- [ ] `pnpm run check` clean.
- [ ] every Hard Rule inlined above still holds.
