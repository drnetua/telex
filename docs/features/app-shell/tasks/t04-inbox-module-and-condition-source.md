---
id: T4
title: "Create the inbox module (InboxSource + sum) and the shared StatusConditionSource contract"
layer: "wiring"
deps: []
blocks: ["T6"]
acs: ["AC-174", "AC-175"]
files_hint: ["backend/app/src/main/kotlin/telex/inbox/", "backend/app/src/main/kotlin/telex/shared/StatusConditionSource.kt", "backend/app/src/main/kotlin/telex/web/package-info.java", "backend/app/src/test/kotlin/telex/inbox/", "backend/app/src/test/kotlin/telex/ModularityTest.kt"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T4 — Create the inbox module (InboxSource + sum) and the shared StatusConditionSource contract

## Place in the sequence

- **Blocked by:** — · **Blocks:** T6 — Serve the pulse with e2e fixture sources · **Wave:** 1 — new contracts with no implementers yet, so it commits green on its own.
- **Lane:** own lane (only task touching `web/package-info.java`).

## Why (user story)

> **As an** Owner
> **I want** the Inbox counter in the navigation to always show how many items wait for me
> **So that** I notice new work without opening the Inbox
>
> — `spec.md §4, US-71, verbatim` · full text: [spec.md](../spec.md)

This task creates the two extension points later epics plug into: Inbox counts and Status Banner conditions.

## Inlined context

> **The Inbox count is summed from sources the item owners provide.** A new core module `inbox` defines `InboxSource.countWaiting(ownerId)` and sums the implementations. Each later epic implements a source in its own module, so the "waiting" state is never duplicated and the shell never changes.
>
> — `sad.md §4, strategic choice 2, verbatim` · decision: [adr/0003](../adr/0003-aggregate-the-inbox-count-from-sources-in-a-new-inbox-module.md)

> - **`inbox`** (new core module, 15th) holds only the `InboxSource` contract and the sum. It depends on `shared` only, and producer modules depend on it (ADR-0003).
> - **`shared`** (kernel) gains the `StatusConditionSource` interface, a type with no beans (ADR-0006).
> - **`web`** (interface) adds the pulse, preferences and timezone-list endpoints. Its `allowedDependencies` gain `inbox`.
> ```
> inbox/  InboxSource  interface: countWaiting(ownerId): Int
>         Inbox        countWaiting(ownerId) = sum over all InboxSource beans
> shared/ StatusConditionSource  interface: activeConditions(ownerId): Set<String> (condition codes), no beans
> ```
>
> — `sad.md §5, building blocks + Internal decomposition, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** `shared` gains its first interface meant to be implemented across modules. Interface only, no Spring annotations, so `ModularityTest` keeps `shared` bean-free (ADR-0006).
>
> — `sad.md §11, risk "shared gains its first interface", abridged` · decision: [adr/0006](../adr/0006-extend-the-shell-through-client-section-and-server-condition-registries.md)

> **Hard rule:** every Inbox and condition source is called with that Owner's id and filters on `owner_id` (AC-175).
>
> — `sad.md §8, Authorization, abridged`

**Fallback:** insufficient or contradicted by the code → read [adr/0003](../adr/0003-aggregate-the-inbox-count-from-sources-in-a-new-inbox-module.md), [adr/0006](../adr/0006-extend-the-shell-through-client-section-and-server-condition-registries.md). Do not guess.

## Data delta

No DB changes. (*Inbox count:* summed live from `InboxSource` implementations; E06 adds no Inbox table — `data-model.md §Not stored, abridged`.)

## API contract

Internal — no API surface.

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

- [ ] `telex/inbox/package-info.java` — `@ApplicationModule(displayName = "Inbox", allowedDependencies = {"shared"})`
- [ ] `telex/inbox/InboxSource.kt` (`fun countWaiting(ownerId: UUID): Int`) and `telex/inbox/Inbox.kt` (`@Component`, sums every `InboxSource` bean for one Owner; 0 with no sources). `OwnerId` lives in `telex.identity` (`identity/Ids.kt`), which neither `inbox` nor `shared` may depend on, so both contracts take the raw `UUID` (`OwnerId.value`)
- [ ] `telex/shared/StatusConditionSource.kt` (`fun activeConditions(ownerId: UUID): Set<String>`), no annotations
- [ ] Add `"inbox"` to `web`'s `allowedDependencies` — `web/package-info.java`
- [ ] Unit test `InboxTest`: no sources → 0; two sources (2 + 3) → 5; each source receives the same Owner id — `src/test/kotlin/telex/inbox/`
- [ ] `ModularityTest` green with 15 modules

## Edge cases

| Case | Behaviour |
|---|---|
| No `InboxSource` bean (production E06) | Sum is 0 |
| Several sources | Sum of each source's count for that Owner only |
| `shared` scanned for beans | None — the interface carries no Spring annotation |

## Definition of Done

- [ ] `InboxTest` passes; `ModularityTest` (`ApplicationModules.verify()`) green with the new module
- [ ] every Hard Rule inlined above still holds
- [ ] `./gradlew spotlessCheck detekt` clean
