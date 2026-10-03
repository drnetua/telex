---
id: T27
title: "Amend AC-211 to per-1M-token prices for image models and register the new components in the design-system inventory"
layer: "docs"
deps: ["T26"]
acs: ["AC-211"]
files_hint: ["docs/features/model-profiles/spec.md", "docs/features/model-profiles/data-model.md", "docs/features/model-profiles/screens.md", "docs/design-system.md", "frontend/src/pages/models/ModelsPage.test.tsx", "backend/app/src/test/kotlin/telex/llm/ModelListParserTest.kt"]
owner: "Anton Husiev"
estimate: "S"
origin: "review-2026-10-03"
status: "todo"
---

# T27 — Amend AC-211 to per-1M-token prices for image models and register the new components in the design-system inventory

## Origin

Follow-up task from the independent review — [review-2026-10-03.md](../_review/review-2026-10-03.md). Each finding below cites `file:line` as of commit `9e411fd`; re-check the lines before editing. The ACs are in [spec.md](../spec.md) §5 (source of truth); contracts in [data-model.md](../data-model.md), [openapi.yaml](../contracts/openapi.yaml), [events.md](../contracts/events.md), [public-api.md](../contracts/public-api.md), [screens.md](../screens.md), [sad.md](../sad.md), [adr/](../adr/).

## Findings to fix

### D1 (stage 1, AC-211) — per-image price never delivered — decision: **amend the spec**
`ModelListParser.kt:72` hard-codes `perImage = null`; `CatalogTab.tsx:20`'s per-image branch never runs; image models show "$x in · $y out per 1M tokens". OpenRouter publishes no per-image price (its `pricing.image_output` is per output image *token*). The owner chose to amend the spec in review.
**Do:**
1. `spec.md` §1: add a "Decision deviation" bullet (same style as the existing ones): image models are priced per 1M tokens like other models because OpenRouter publishes no per-image price; recorded by review 2026-10-03.
2. `spec.md` §5 AC-211: replace the "price per image for models that create images" wording with the per-1M-token price, keeping the rest verbatim.
3. `data-model.md`: on `price_per_image`, note it stays NULL in E10 (no source) and is reserved.
4. A test asserting that an image-output model in the catalog shows its per-1M-token price (Vitest in `ModelsPage.test.tsx`, and/or a parser unit test). Optionally remove the now-dead per-image UI branch only if trivially safe.
Do NOT edit `tasks/tracker.md` (the lead updates it).

### F5 (stage 2, screens.md "New components") — inventory not registered
`docs/design-system.md:41-57` (Component inventory) lacks ModelProfileCard, ChainEditor, ModelChooser, the `Cost` `precision` prop, and the ports of ModelProfilePicker (C-22), Cost (C-27), FilterBar (C-28), DataTable (C-29). **Do:** add one row each in the table's existing format (`file:line`, states, notes) reflecting the code as it is now (after T26), then flip the "Registered in design-system" column in `screens.md` (~:239).

## Definition of Done

spec.md §1 records the decision deviation and AC-211 reads per-1M-token prices for image models; data-model.md notes price_per_image is unused in E10; a test asserts an image model in the catalog shows its per-1M-token price; docs/design-system.md lists every new or ported component and screens.md flips their Registered column.

Test first: write the failing test(s) for each finding, run them, classify the first run (GOOD red / BAD red / false-pass / NON-red) and quote the failing line before production code. Never weaken an existing test except where a finding above says the expectation changes by an owner decision.
