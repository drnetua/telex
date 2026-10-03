---
id: T6
title: "Serve GET /api/v1/pulse and the e2e-profile fixture Inbox and condition sources"
layer: "ports"
deps: ["T4"]
blocks: ["T17", "T18"]
acs: ["AC-173", "AC-174", "AC-175", "AC-178"]
files_hint: ["backend/app/src/main/kotlin/telex/web/api/PulseController.kt", "backend/app/src/main/kotlin/telex/web/e2e/", "backend/app/src/integrationTest/kotlin/telex/web/PulseApiIT.kt"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T6 — Serve GET /api/v1/pulse and the e2e-profile fixture Inbox and condition sources

## Place in the sequence

- **Blocked by:** T4 — Create the `inbox` module and the shared `StatusConditionSource` · **Blocks:** T17 — e2e: navigation, return after sign-in and the shell sweep, T18 — e2e: Inbox counter and Status Banners · **Wave:** 2.
- **Lane:** own lane.

## Why (user story)

> **As an** Owner
> **I want** the Inbox counter in the navigation to always show how many items wait for me
> **So that** I notice new work without opening the Inbox
>
> — `spec.md §4, US-71, verbatim` · full text: [spec.md](../spec.md)

This task is the server side of the live channel: one background answer carrying the count and the active conditions.

## Inlined context

> **One background pulse every 3 s is the live channel.** Each visible tab asks `GET /api/v1/pulse` (marked background, so it doesn't extend the session). The answer carries the Inbox count and the active Status Banner conditions, and later epics add fields to it instead of opening new channels.
>
> — `sad.md §4, strategic choice 1, abridged` · decision: [adr/0002](../adr/0002-poll-one-background-pulse-every-3-seconds-for-live-signals.md)

> Seed flow 1: pulse, marked background → resolve the Sign-in Session without counting activity → *no live session*: problem unauthenticated or session-ended · *live session*: waiting count for this Owner (sum of sources) → active Status Banner conditions for this Owner → inbox count and condition codes.
>
> — `sad.md §6, Critical flow 1, abridged` · full text: [sad.md](../sad.md)

> A Spring profile `e2e` (off by default, never set in `compose.yaml`) registers a fixture `InboxSource` and a fixture `StatusConditionSource`, plus a fixture endpoint that prepares their values. This is the only way to test AC-174 and AC-178 before a real producer exists.
>
> — `sad.md §5, verbatim` · full text: [sad.md](../sad.md)

> **Hard rule:** Off by default and never set in `compose.yaml`. Its beans are `@Profile("e2e")`, and the fixture endpoint only changes the calling Owner's fixture values. `/sdd:review` checks that no production config sets it.
>
> — `sad.md §11, risk "e2e Spring profile", abridged`

> **Hard rule:** Each source must be one indexed count by `owner_id`. Every Inbox and condition source is called with that Owner's id (AC-175). The pulse is not logged per request.
>
> — `sad.md §11 + §8 Authorization/Logging, abridged`

**Fallback:** insufficient or contradicted by the code → read [openapi.yaml](../contracts/openapi.yaml) `getPulse`/`setPulseFixture` and [adr/0002](../adr/0002-poll-one-background-pulse-every-3-seconds-for-live-signals.md). Do not guess.

## Data delta

No DB changes. Fixture values are held in memory per Owner, never stored.

## API contract

- `GET /api/v1/pulse` (`getPulse`), header `X-Telex-Background: 1` → `200 Pulse {inboxCount: int ≥ 0 (uncapped), conditions: StatusConditionCode[] (unique, kebab-case)}`; production E06 answers `{inboxCount: 0, conditions: []}` · `401` `unauthenticated` / `session-ended`. Clients ignore unknown fields.
- `PUT /api/v1/e2e-fixtures/pulse` (`setPulseFixture`), `e2e` profile only, body `Pulse`, `X-XSRF-TOKEN` → `204`; next pulse reports them for the calling Owner · `400 validation-failed` (e.g. `inboxCount` field code `min`) · `401` · `403` · `404 not-found` when the profile is off.

— `contracts/openapi.yaml, operationIds getPulse, setPulseFixture, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-173 — authorization

> **Given** a visitor without a live Sign-in Session
> **When** they open a link to any section, including a "Coming soon" one, or take their next action in a tab that was open when the session stopped
> **Then** they see nothing of the shell (no sections, no counter, no banners): a person who never signed in or signed out sees the sign-in page, and a session that ran out or was revoked shows the "Session ended" page; when sign-in finishes in that same browser (Sign-in Code, Passkey, or a Sign-in Link opened there) they land on the section the link pointed to, otherwise on the Inbox, and a brand-new account first gets the passkey offer
>
> — `spec.md §5, AC-173, verbatim` · full text: [spec.md](../spec.md)

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

### AC-178 — domain invariant

> **Given** an Owner on a screen where one or more Status Banner conditions hold
> **When** they look for a way to close a banner, or a second condition starts while the first still holds
> **Then** no banner can be dismissed while its cause remains ("a banner lives exactly as long as its cause"), and when several conditions hold only the most important banner shows, chosen by a fixed importance order (§8), with "N more" that lists the others, each with its own action
>
> — `spec.md §5, AC-178, verbatim` · full text: [spec.md](../spec.md)

This task owns the server half: the pulse returns `401` without a live session (AC-173), the per-Owner count (AC-174/175) and every active condition code (AC-178). Ordering and display are T12/T13.

## Checklist

- [ ] `PulseController`: `GET /api/v1/pulse` → `Inbox.countWaiting(owner)` + union of every `StatusConditionSource.activeConditions(owner)` — `web/api/PulseController.kt`
- [ ] `@Profile("e2e")` fixture `InboxSource` + `StatusConditionSource` backed by an in-memory per-Owner map, and the `PUT /api/v1/e2e-fixtures/pulse` controller — `web/e2e/`
- [ ] `PulseApiIT`: default `0`/`[]`; `401` both codes; background pulse doesn't bump `last_activity_at`; under `@ActiveProfiles("e2e")` Owner A set to 5 and Owner B sees 0; conditions round-trip; fixture path `404` without the profile; contract validated with `ContractValidator`

## Edge cases

| Case | Behaviour |
|---|---|
| No live session | `401 unauthenticated` or `session-ended`; no count leaks |
| Owner A's fixture set to 5 | Owner B's pulse still `inboxCount: 0` |
| Two sources report the same code | Appears once (`uniqueItems`) |
| `PUT` fixture with `inboxCount: -1` | `400 validation-failed` |
| `e2e` profile off | Fixture path `404 not-found`; no fixture beans |

## Definition of Done

- [ ] `PulseApiIT` covers every edge case and passes contract validation
- [ ] No production config (`application.yaml`, `compose.yaml`) activates `e2e`
- [ ] every Hard Rule inlined above still holds
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green
