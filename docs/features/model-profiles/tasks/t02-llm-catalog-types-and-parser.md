---
id: T2
title: "Define the llm catalog value types, the slot-fit rule and the OpenRouter model-list parser"
layer: "domain"
deps: []
blocks: ["T3", "T5", "T7", "T8"]
acs: ["AC-211"]
files_hint: ["backend/app/src/main/kotlin/telex/llm/ModelId.kt", "backend/app/src/main/kotlin/telex/llm/ModelSlotKind.kt", "backend/app/src/main/kotlin/telex/llm/CatalogModel.kt", "backend/app/src/main/kotlin/telex/llm/internal/openrouter/ModelListParser.kt", "backend/app/src/test/kotlin/telex/llm/", "backend/app/src/test/resources/openrouter/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T2 — Define the llm catalog value types, the slot-fit rule and the OpenRouter model-list parser

## Place in the sequence

- **Blocked by:** — · **Blocks:** T3 — Store the catalog snapshot in Postgres, load it at start and hold it in memory, T5 — Build the in-call fallback loop in llm with one attempt per model, a per-attempt timeout and outcome classification, T7 — Model the custom profile aggregate, ProfileRef and the profile rules in plain Kotlin, T8 — Resolve slots against the current catalog and estimate the price per 100 runs · **Wave:** 1 — pure Kotlin, no upstream code; parallel with T1, T7 and T16.
- **Lane:** own lane (`telex/llm` root types are first written here; T3–T6 build on them).

## Why (user story)

> **As an** Owner
> **I want** to see the models teleX can use, with what each one can do and what it costs
> **So that** I can tell which models suit text, photos or images before I build a profile
>
> — `spec.md §4, US-80, verbatim` · full text: [spec.md](../spec.md)

This task decides which provider models enter the Model Catalog and how their capabilities and prices are read, so the catalog lists only models that fit a slot.

## Inlined context

> Module boundaries: `llm` is an integration ACL that may depend on `shared` only. `web` may depend on core modules (incl. `agents`) but not on `llm`. `agents` may depend on `llm` (`package-info.java` of each). This feature keeps all of them unchanged ([feature ADR-0002](adr/0002-profiles-in-agents-llm-thin-acl.md)).
>
> — `sad.md §2, Technical constraints, Module boundaries, verbatim` · full text: [sad.md](../sad.md)

> | `model_id` | VARCHAR(200) | PK | Provider's id, e.g. `openai/gpt-4o-mini` (`ModelId`) |
> | `provider` | VARCHAR(100) | NOT NULL | Shown as the provider (AC-211). The adapter derives it from the id prefix |
> | `takes_text` | BOOLEAN | NOT NULL | What it accepts (AC-211). Slot fit: text = `takes_text AND produces_text` |
> | `takes_images` | BOOLEAN | NOT NULL | Vision = `takes_images AND produces_text` (AC-211, AC-216) |
> | `produces_images` | BOOLEAN | NOT NULL | Image = `produces_images` |
> | `input_price_per_mtok` | NUMERIC(14,6) | NULL, `≥ 0` | USD per million input tokens. NULL = unknown → "Price unknown" (AC-210). 0 → "Free" |
> | `price_per_image` | NUMERIC(14,6) | NULL, `≥ 0` | USD per created image, for image models (AC-211) |
> | `context_length` | INTEGER | NULL, `> 0` | "How much text it can take at once" (AC-211). NULL when the provider doesn't say |
>
> Modalities other than text and images (audio, files) aren't stored, because no slot uses them.
>
> — `data-model.md §Entities, table model_catalog_entry, abridged` · full text: [data-model.md](../data-model.md)

> - Strings: `VARCHAR(N)`. […] A model id is 200 […]. The adapter skips and logs a longer one (sad §11 defensive parsing).
> - Money: `NUMERIC(14, 6)` USD (Kotlin `BigDecimal`, sad §8), stored **per million tokens** and **per image**, the units the catalog shows (AC-211). The adapter converts OpenRouter's per-token decimal strings by shifting the decimal point, with no rounding.
>
> — `data-model.md §Conventions applied, Strings + Money, abridged` · full text: [data-model.md](../data-model.md)

> | OpenRouter's `/models` schema or error bodies change, so capability, price or outcome mapping is wrong | Medium | Parse defensively: skip a model with unknown modalities and log it. Map errors by HTTP status first and by the body second. WireMock fixtures are copied from real responses |
>
> — `sad.md §11, Risks, row 2, verbatim` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes. (The types mirror `model_catalog_entry`; T3 persists them.)

## API contract

Internal — no HTTP surface. The `llm` module-root types this task introduces (indicative names, sad §5): `ModelId` (value class over the provider string), `ModelSlotKind { TEXT, VISION, IMAGE }`, `Modality { TEXT, IMAGE }`, `CatalogModel(modelId, name, provider, takes, produces, inputPerMtok, outputPerMtok, perImage, contextLength)` with `fits(slot)` / `slots`. They match the contract's `CatalogModel.slots` derivation: "text = takes and produces text, vision = takes images and produces text, image = produces images".

— `contracts/openapi.yaml, schema CatalogModel.slots, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-211 — happy (US-80)

> **Given** a signed-in Owner and a loaded Model Catalog
> **When** the Owner opens the catalog on the Models page, searches by name and filters by the vision slot
> **Then** the Owner sees only models that can understand images and answer in text, each with its name, provider, what it accepts and produces, its price in US dollars (per million input and output tokens, or per image for models that create images) and how much text it can take at once, and sees when the catalog was last updated; the catalog lists only models that fit at least one slot (text: takes and produces text; vision: takes images and produces text; image: creates images)
>
> — `spec.md §5, AC-211, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Add `ModelId`, `ModelSlotKind`, `Modality`, `CatalogModel` (+ slot fit) at the `llm` root — `backend/app/src/main/kotlin/telex/llm/`
- [ ] Write `ModelListParser` (pure: JSON → `List<CatalogModel>` + skipped list) in `backend/app/src/main/kotlin/telex/llm/internal/openrouter/`
- [ ] Copy a real OpenRouter `/models` response into `backend/app/src/test/resources/openrouter/models.json`, trimmed to a handful of text, vision, image, audio-only, free and unpriced models; read the field names from it (do not guess them)
- [ ] Unit tests in `backend/app/src/test/kotlin/telex/llm/`: slot fit per slot; price shift per-token → per-million exact (`0.00000015` → `0.150000`); provider from the id prefix; skip cases below

## Edge cases

| Case | Behaviour |
|---|---|
| Model that fits no slot (audio-only, embedding) | Skipped, not stored (`model_catalog_entry_fits_a_slot_ck`) |
| Unknown or missing modalities | Skipped and logged (sad §11) |
| Id longer than 200 characters | Skipped and logged |
| Price field missing or not a decimal | That price is `null` ("Price unknown" downstream), the model stays |
| Negative price or non-positive context length | Treated as unknown (`null`), never stored negative |
| Image model with no identifiable per-image price in the real response | `pricePerImage = null`; note it in the PR |

## Definition of Done

- [ ] slot-fit and parser unit tests pass over the real-response fixture
- [ ] `ModularityTest` green — the new types live at the `llm` root and depend on `shared` only
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
