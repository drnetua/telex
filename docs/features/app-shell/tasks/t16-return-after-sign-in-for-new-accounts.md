---
id: T16
title: "Land a brand-new account on the remembered section after the passkey step"
layer: "ui"
deps: []
blocks: ["T17"]
acs: ["AC-173"]
files_hint: ["frontend/src/app/landing.ts", "frontend/src/app/landing.test.ts", "frontend/src/pages/create-passkey/CreatePasskeyPage.tsx", "frontend/src/pages/create-passkey/CreatePasskeyPage.test.tsx"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "S"
status: "todo"
---

# T16 — Land a brand-new account on the remembered section after the passkey step

## Place in the sequence

- **Blocked by:** — · **Blocks:** T17 — e2e: navigation, return after sign-in and the shell sweep · **Wave:** 1 — touches only E01 landing code.
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** one navigation that shows every part of teleX on every signed-in screen
> **So that** I always know where I am and can get anywhere in one or two taps
>
> — `spec.md §4, US-70, verbatim` · full text: [spec.md](../spec.md)

This task makes a section link survive sign-up: the new Owner sees the passkey offer first, then the linked section.

## Inlined context

> **Return after sign-in:** Kept from E01: a refused page is remembered in localStorage `telex.destination` of the browser that is refused, first refusal wins, never an auth page, single-slash paths only. **Changed:** for a brand-new account the sign-in no longer takes the destination; SCR-09 takes it after the passkey is created or skipped, so the Owner lands on the linked section. A Sign-in Link opened in another browser finds no destination and lands on the Inbox.
>
> — `sad.md §8, Return after sign-in, verbatim` · full text: [sad.md](../sad.md)

> SCR-09 `success` / `not-now` / `unsupported` Continue / `no-flag`: → **the section remembered before sign-in, cleared on use, or SCR-10 when there's none**.
>
> — `screens.md §SCR-09, abridged` · full text: [screens.md](../screens.md)

> **Hard rule:** Change `landing.ts`, the passkey page and `landing.test.ts` in one task; E01 tests that expect a new account to land on the Inbox will fail otherwise.
>
> — `sad.md §11, risk "return-after-sign-in change", abridged`

Current code: `landAfterSignIn(createdAccount)` calls `takeRememberedDestination()` before checking `createdAccount`, so a new account loses the destination.

**Fallback:** read [sad.md](../sad.md) Flow 5. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — no API surface (uses the existing E01 sign-in and passkey operations unchanged).

## Acceptance criteria

### AC-173 — authorization

> **Given** a visitor without a live Sign-in Session
> **When** they open a link to any section, including a "Coming soon" one, or take their next action in a tab that was open when the session stopped
> **Then** they see nothing of the shell (no sections, no counter, no banners): a person who never signed in or signed out sees the sign-in page, and a session that ran out or was revoked shows the "Session ended" page; when sign-in finishes in that same browser (Sign-in Code, Passkey, or a Sign-in Link opened there) they land on the section the link pointed to, otherwise on the Inbox, and a brand-new account first gets the passkey offer
>
> — `spec.md §5, AC-173, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `landAfterSignIn(true)` returns `/welcome/passkey` without taking the destination — `frontend/src/app/landing.ts`
- [ ] SCR-09 takes the destination (or `/inbox`) on created, Not now, unsupported Continue and no-flag — `frontend/src/pages/create-passkey/CreatePasskeyPage.tsx`
- [ ] Vitest: update `landing.test.ts`; add page tests for each exit

## Edge cases

| Case | Behaviour |
|---|---|
| New account, `/runs` remembered | SCR-09 → `/runs`, destination cleared |
| New account, nothing remembered | SCR-09 → `/inbox` |
| Existing account, `/runs` remembered | Straight to `/runs` (unchanged) |
| SCR-09 reloaded without the flag | Remembered section or `/inbox` |

## Definition of Done

- [ ] Vitest for every edge case passes; E01 landing tests updated
- [ ] `pnpm run check` clean
