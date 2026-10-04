---
id: T19
title: "Add Playwright end-to-end tests for the Models page at 360 px and 1280 px with axe and a 500-model load timing"
layer: "tests"
deps: ["T15", "T18"]
blocks: []
acs: ["AC-51", "AC-211", "AC-213", "AC-220"]
files_hint: ["e2e/tests/models.spec.ts", "e2e/support/", "compose.yaml", ".github/workflows/ci.yml"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T19 — Add Playwright end-to-end tests for the Models page at 360 px and 1280 px with axe and a 500-model load timing

## Place in the sequence

- **Blocked by:** T15 — Expose the write endpoints for profiles and the default profile with their problem codes, T18 — Build the profile editor modal with the chain editor and the model chooser · **Blocks:** — · **Wave:** 8 — last: needs the whole HTTP surface (T15) and every screen (T18).
- **Lane:** own lane (`e2e/`, compose, CI workflow).

## Why (user story)

> **As an** Owner
> **I want** to compare the system profiles and my own profiles by estimated price and pick one as my default
> **So that** my AI helpers start on a model choice whose cost I understand
>
> — `spec.md §4, US-13, verbatim` · full text: [spec.md](../spec.md)

> **As an** Owner
> **I want** to see the models teleX can use, with what each one can do and what it costs
> **So that** I can tell which models suit text, photos or images before I build a profile
>
> — `spec.md §4, US-80, verbatim` · full text: [spec.md](../spec.md)

> **As an** Owner
> **I want** to create, duplicate, edit and delete my own Model Profiles, with a Fallback Chain in each slot
> **So that** my helpers use the models I trust, with backups I chose
>
> — `spec.md §4, US-81, verbatim` · full text: [spec.md](../spec.md)

This task proves the Models page works end to end in a real browser at phone and desktop widths, accessibly and fast with a full-size catalog.

## Inlined context

> | Models page load (catalog + profiles + picker) | p95 ≤ 1 s with a catalog of 500 models | Playwright timing on the CI build + server request-duration metric |
> | Responsive + accessible | the Models page and the profile editor work at 360 px and 1280 px and meet WCAG 2.2 AA | Playwright at both widths + automated accessibility scan with 0 violations |
>
> — `spec.md §6, NFR rows Models page load + Responsive + accessible, verbatim` · full text: [spec.md](../spec.md)

> **How verify:** Playwright timing on the CI build with a 500-model fake catalog, plus the server request-duration metric (`http.server.requests` for `/api/models/**`). Playwright at both widths plus an automated accessibility scan with 0 violations.
>
> — `sad.md §10, QG-4, abridged` · full text: [sad.md](../sad.md)

> Tests never reach OpenRouter: WireMock serves `/models` and the chat and image endpoints.
>
> — `sad.md §7, Local, abridged` · full text: [sad.md](../sad.md)

**Existing setup:** `e2e/playwright.config.ts` runs projects `phone` (360×800) and `desktop` (1280×800) against `docker compose up` (app :8080, Mailpit :8025) and does not start the stack; `e2e/support/flows.ts` signs an Owner in through Mailpit. Point the app container's `telex.llm.openrouter.base-url` at a WireMock service with a 500-model `/models` mapping (`aCatalogOf(count = 500)`, data-model §Test fixtures) and a dummy key.

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — exercises the `contracts/openapi.yaml` surface through the UI; reads the `http.server.requests` metric for `/api/v1/models/**`.

## Acceptance criteria

### AC-51 — happy (US-13)

> **Given** a signed-in Owner whose default profile is still Balanced (every new Owner starts with it) and who has one custom profile
> **When** the Owner opens the profile picker on the Models page and switches the default to Careful
> **Then** each option (Fast and cheap, Balanced, Careful and the custom profile) shows an estimated price per 100 runs, Careful becomes the default, and the Owner sees a confirmation naming it
>
> — `spec.md §5, AC-51, verbatim` · full text: [spec.md](../spec.md)

### AC-211 — happy (US-80)

> **Given** a signed-in Owner and a loaded Model Catalog
> **When** the Owner opens the catalog on the Models page, searches by name and filters by the vision slot
> **Then** the Owner sees only models that can understand images and answer in text, each with its name, provider, what it accepts and produces, its price in US dollars (per million input and output tokens, or per image for models that create images) and how much text it can take at once, and sees when the catalog was last updated; the catalog lists only models that fit at least one slot (text: takes and produces text; vision: takes images and produces text; image: creates images)
>
> — `spec.md §5, AC-211, verbatim` · full text: [spec.md](../spec.md)

### AC-213 — happy (US-81)

> **Given** a signed-in Owner
> **When** the Owner duplicates Balanced (the copy is first named "Balanced copy", then "Balanced copy 2" if that is taken), renames it "Cheap vision", puts two models in the text slot, moves the second one up so it becomes the main model, puts one model in the vision slot, leaves the image slot empty and saves
> **Then** "Cheap vision" appears among the Owner's own profiles with its slots, chains in the chosen order, the image slot shown as "Not used" and the estimated price per 100 runs, and the Owner sees "Profile saved"; models of Balanced that were missing from the Model Catalog at the time are copied too, marked "Not in the catalog"
>
> — `spec.md §5, AC-213, verbatim` · full text: [spec.md](../spec.md)

### AC-220 — happy (US-81)

> **Given** an Owner whose default profile is their custom profile "Cheap vision"
> **When** the Owner deletes "Cheap vision" and confirms
> **Then** the profile disappears from their list, the default goes back to Balanced, and the Owner is told that Balanced is now the default
>
> — `spec.md §5, AC-220, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] WireMock OpenRouter service (500 models incl. vision and image ones) in the compose stack used by e2e (profile or override) — `compose.yaml`
- [ ] `e2e/tests/models.spec.ts`: browse + search + vision filter; choose Careful; duplicate Balanced → rename → reorder → save; delete the default custom profile; axe on SCR-66 and SCR-34
- [ ] Timing: page load to visible picker + catalog, sampled ≥ 20 times, p95 ≤ 1 s — `e2e/tests/models.spec.ts`
- [ ] CI picks the new spec up (adjust `.github/workflows/ci.yml` only if the stack needs the WireMock service)

## Edge cases

| Case | Behaviour |
|---|---|
| Two parallel projects (phone, desktop) on one stack | Each test signs up its own Owner (`user-<uuid>@example.test`) |
| CI runner slower than local | p95 is asserted on the CI build, as the NFR says; failures report the samples |
| Catalog not loaded when the test starts | Wait for `state = current` via the API before the journey |

## Definition of Done

- [ ] the four journeys pass at both widths in CI
- [ ] axe: 0 violations on SCR-66 (both tabs) and SCR-34
- [ ] the p95 ≤ 1 s assertion passes with 500 models
- [ ] every Hard Rule inlined above still holds
- [ ] `pnpm run check` clean; ESLint and `tsc --noEmit` 0 warnings
