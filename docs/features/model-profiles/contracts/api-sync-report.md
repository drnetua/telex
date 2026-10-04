---
status: Draft
owner: "Anton Husiev (Backend Lead)"
updated_at: "2026-10-03"
feature_size: S
---

# API sync report — model-profiles

**Inputs read:** `data-model.md` ✓ (present, default path) · `sad.md` ✓ (`target_surfaces: [backend-service, web-frontend]` → OpenAPI + `events.md`. §6 has 6 critical flows, §8 has the error codes and events) · `spec.md` ✓ (§4 US-13, US-80…US-83; §5 21 ACs) · `ux-flows.md` (SCR-66, SCR-34, SCR-91) · feature ADR-0001…0005 · platform-skeleton `contracts/openapi.yaml` (the conventions) · `telex/shared/Problems.kt`, `telex/web/ProblemHandler.kt`.
**Outputs:** `contracts/openapi.yaml` (OpenAPI 3.1, 8 operations; `spectral:oas` lint: 0 errors, 0 warnings, examples validated), `contracts/events.md` (3 events), `contracts/public-api.md` (the in-process `ProfileCalls` port; it covers AC-224, AC-228 and AC-229, which no HTTP operation can).
**Size / route:** S / quick (from `.size` / `.route`).

## A. Field origins

| schema_path | origin | confidence |
|---|---|---|
| getModelCatalog.state | derived — model_catalog_state (last_refreshed_at NULL / last_failed_at > last_refreshed_at) + key setting (AC-212, AC-226) | high |
| getModelCatalog.lastRefreshedAt | data-model.md → model_catalog_state.last_refreshed_at | high |
| getModelCatalog.lastFailedAt | data-model.md → model_catalog_state.last_failed_at | high |
| getModelCatalog.models[].modelId | data-model.md → model_catalog_entry.model_id VARCHAR(200) | high |
| getModelCatalog.models[].name | data-model.md → model_catalog_entry.name VARCHAR(200) | high |
| getModelCatalog.models[].provider | data-model.md → model_catalog_entry.provider VARCHAR(100) | high |
| getModelCatalog.models[].takes / produces | data-model.md → model_catalog_entry.takes_text, takes_images / produces_text, produces_images | high |
| getModelCatalog.models[].slots | derived — slot-fit rule over the four booleans (AC-211; data-model fits_a_slot_ck) | high |
| getModelCatalog.models[].inputPricePerMillionTokens | data-model.md → model_catalog_entry.input_price_per_mtok NUMERIC(14,6) NULL | high |
| getModelCatalog.models[].outputPricePerMillionTokens | data-model.md → model_catalog_entry.output_price_per_mtok | high |
| getModelCatalog.models[].pricePerImage | data-model.md → model_catalog_entry.price_per_image | high |
| getModelCatalog.models[].contextLength | data-model.md → model_catalog_entry.context_length INTEGER NULL (> 0) | high |
| *.ref / ProfileRef | data-model.md → system_profile_key CHECK enum / model_profile.id (decision 1) | high |
| *.name (ModelProfile) | data-model.md → model_profile.name VARCHAR(40), trimmed; system names from settings (ADR-0005) | high |
| *.slots.<slot>.chain[].modelId | data-model.md → model_profile_slot_model.model_id, ordered by position (1–3) | high |
| *.slots.<slot>.chain[].name | data-model.md → model_catalog_entry.name (null when absent) | high |
| *.slots.<slot>.chain[].availability | derived at read time (sad §4, §8 Availability evaluation); `not-capable` from AC-227 | medium |
| *.slots.<slot>.state / currentModelId | derived at read time (AC-10, AC-213, AC-223) | medium |
| *.pricePer100Runs.state / amount | derived — sad §8 Price estimate over model_catalog_entry prices (spec §6) | medium |
| *.choosable | derived — text slot has an available model (AC-223) | medium |
| listModelProfiles.defaultProfile | data-model.md → default_model_profile (no row = Balanced) | high |
| listModelProfiles.aiConfigured | derived — `telex.llm.openrouter.api-key` set (sad §7, AC-226) | medium |
| listModelProfiles.customProfileLimit | spec AC-218 (constant 20, no column) | medium |
| listModelProfiles.items order | data-model.md → model_profile.created_at | high |
| getModelProfileDraft.name | derived — "<source> copy [N]" checked against model_profile_owner_id_lower_name_uq (AC-213) | high |
| getModelProfileDraft.duplicatedFrom / createModelProfile.duplicatedFrom | sad §6 flow 5 "models not in the stored profile or the duplicated source"; not stored (data-model "Not stored") | medium |
| createModelProfile / updateModelProfile .name | data-model.md → model_profile.name (input bound 200, trimmed to 1–40) | high |
| createModelProfile / updateModelProfile .slots.* | data-model.md → model_profile_slot_model (slot, position, model_id) | high |
| deleteModelProfile.defaultReset | derived — row count of the default delete (data-model §default_model_profile) | high |
| setDefaultModelProfile.profile | data-model.md → default_model_profile.system_profile_key / custom_profile_id | high |
| event ModelCatalogRefreshed.refreshedAt / modelCount | data-model.md → model_catalog_state.last_refreshed_at / count(model_catalog_entry) | high |
| event ModelProfileDeleted.* | data-model.md → model_profile.owner_id, id; default row deleted | high |
| event ModelCallFinished.* | data-model.md → model_call columns | high |
| ProfileCalls result.* | data-model.md → model_call, model_call_attempt (outcome CHECK enums) | high |

No field lacks an origin. The `medium` rows are computed views that sad §4 says must never be stored. They have no column on purpose, and they won't tighten on `--reconcile`.

## B. Drift findings

1. **Endpoint ↔ data-model** *(core)* — ✓ The catalog reads `model_catalog_entry` + `model_catalog_state`. The profile operations read and write `model_profile` + `model_profile_slot_model`. Delete also clears `default_model_profile`. The default-profile operation writes `default_model_profile`. `model_call` and `model_call_attempt` have no HTTP endpoint (no screen in E10, ux-flows "Model call outcomes have no screen"). They are written by the `ProfileCalls` port (`public-api.md`). Accepted, not an orphan.
2. **Error code ↔ repo error definition** *(core)* — ✓ with additions. The repo has no central registry: codes are string literals in `DomainProblem(...)` calls and `ProblemHandler` (`validation-failed`, `internal-error`, `not-found`, `unauthenticated`, `forbidden`, `session-ended` exist). The contract's `ErrorCode` enum is the registry. **New codes for `implement`:** `profile-limit-reached`, `system-profile-read-only`, `no-text-model`, `ai-not-configured`. **New field codes:** `name-required`, `name-too-long`, `name-taken`, `name-reserved`, `text-slot-required`, `model-not-capable`, `slot-full`, `model-duplicate`, `model-left-catalog`. Each needs a `frontend/src/messages.ts` entry. sad §8's `profile-not-found` is **superseded by `not-found`** (user decision 2026-10-03, AC-222: one not-found for SCR-91).
3. **Validation ↔ constraint** *(core)* — ✓
   - `ModelId` ≤ 200.
   - Name output ≤ 40. Input is bounded at 200, so a longer name reaches the domain rule and gets `name-too-long`.
   - `SlotKind`, `SystemProfileKey` and the attempt/call outcome enums = the data-model CHECKs.
   - Chain output `maxItems: 3` = `position BETWEEN 1 AND 3`. Input arrays are deliberately unbounded so a 4th model gets the domain code `slot-full`, not a bean-validation code (AC-217).
   - `UsdAmount` `^[0-9]{1,8}(\.[0-9]{1,6})?$` = NUMERIC(14,6). `contextLength ≥ 1` = CHECK `> 0`.
   - Uniqueness of the name ignoring case (`name-taken`) and of a model per slot (`model-duplicate`) mirror the two unique indexes.
4. **OpenAPI ↔ sequence** *(supporting)* — ✓ with two sequence gaps:
   - Flow 3 (open the page): the `update-failed`, `not-loaded` and `not-configured` catalog states; the `fallback`, `no-model-available` and `not-used` slot states; Background refetches.
   - Flow 4 (default): the four price states → `PricePer100Runs.state`. Not found → 404. No text model → 409 `no-text-model`.
   - Flow 5 (create/duplicate/edit): limit on open → 409 on the draft. Edit by id not own → 404. Edit system → 409 on PUT (the SPA also refuses client-side). Broken rules / newly added model left catalog → 400 `errors[]`. Limit or name taken meanwhile → 409 / 400 `name-taken`. AI not configured → 409.
   - Flow 6 (delete): deleted default → `defaultReset: true`. Deleted not default → `false`. Not own → 404. System → 409.
   - Flows 1 and 2 (call, refresh) have no HTTP side. They map to `public-api.md` and `events.md`.
   - **Gap 1:** flow 5 shows `ai-not-configured` only on save, but AC-226 ("custom profiles can't be edited") also refuses the draft (Create / Duplicate). The contract answers 409 `ai-not-configured` on the draft too.
   - **Gap 2:** flow 1 has no branch for a `Custom` ref that isn't the caller's (or no longer exists, e.g. deleted while E14 holds it). `public-api.md` answers `Failed(PROFILE_NOT_FOUND)` without a call record.
   - Orphan sequences: none.

### Resolutions (4-state)

| # | Finding | Action | Note |
|---|---|---|---|
| 1 | sad §8 `profile-not-found` vs E01 `not-found` | Fix the contract (user: reuse `not-found`) | sad §8's code list is superseded by `ErrorCode` here |
| 2 | ProfileRef wire shape (E09 contract) | Fix the contract (user: tagged object in JSON, bare key-or-uuid in paths) | Paths answer 409 for a system key on PUT/DELETE (AC-219) |
| 3 | Money encoding | Fix the contract (user: decimal string USD) | `UsdAmount`; price per 100 runs in whole cents |
| 4 | sad §5/§8 say `/api/models/**`, repo uses `/api/v1/...` | Accept as is | Contract follows the platform-skeleton prefix: `/api/v1/models/**`. The sad wording is shorthand |
| 5 | Gap 1: `ai-not-configured` on the draft | Save as OQ (owner `sequences`) | Behaviour fixed in the contract |
| 6 | Gap 2: `PROFILE_NOT_FOUND` in the call path | Save as OQ (owner `sequences`) | Behaviour fixed in `public-api.md` |

## C. Deviations from the sdd contract defaults

Inherited from platform-skeleton (same authority, unchanged):

| Default | This contract | Authority |
|---|---|---|
| `BearerAuth` global | `SessionCookie` + `X-XSRF-TOKEN` on POST/PUT/DELETE | platform-skeleton ADR-0001 |
| `{code, message, details?}` envelope | RFC 9457 problem+json + `code`, `errors[]` | architecture-map §Conventions |
| `code` = `module.error_name` | kebab-case DNS-1123 label | platform-skeleton user decision 2026-10-02 |
| Cursor pagination | whole lists: catalog ≤ ~500 models filtered in the browser (sad §6 flags), profiles ≤ 23 | sad §6, AC-218 |
| Idempotency-Key | none: PUT is idempotent, a retried create answers `name-taken` | platform-skeleton user decision |

Decided here: `409` for `ai-not-configured` (an installation state, not an outage, so it doesn't trigger SCR-93). Delete answers `200` with a body (the SPA needs `defaultReset`). The draft is a read-only `GET /profile-draft?from=`.

## Open questions raised

- [ ] Draw the `ai-not-configured` refusal on Create / Duplicate (draft request) in Critical flow 5. The contract already answers 409 there. — owner: `sequences` (Anton Husiev), due: before the contract is finalized (`/sdd:api model-profiles --reconcile`)
- [ ] Draw the "profile not found" branch (custom ref not the caller's or deleted) in Critical flow 1. `public-api.md` already returns `Failed(PROFILE_NOT_FOUND)` with no call record. — owner: `sequences` (Anton Husiev), due: before the contract is finalized

## Self-check

Structural self-check passed: 8/8 operations map to a §4 story. All 21 §5 ACs map to ≥1 operation, response or port rule. Every §6 `alt` branch has a response. Every operation has request (where it has a body), success and error examples with placeholder data only (`test/...` model ids; no personal data).

| AC | Operation / response |
|---|---|
| AC-51 | `listModelProfiles` (`pricePer100Runs`, `defaultProfile`), `setDefaultModelProfile` 200 |
| AC-210 | `PricePer100Runs.state` `unknown` / `free` / `under-one-cent`, `choosable` |
| AC-211 | `getModelCatalog` (`slots` filter, prices, `contextLength`, `lastRefreshedAt`) |
| AC-212 | `getModelCatalog.state` `update-failed` / `not-loaded` |
| AC-213 | `getModelProfileDraft?from=balanced` (copy name, `not-in-catalog`), `createModelProfile` 201 (`not-used`) |
| AC-214 | 400 `name-required` / `name-too-long` / `name-taken` / `name-reserved` |
| AC-215 | 400 `text-slot-required` |
| AC-216 | 400 `model-not-capable` |
| AC-217 | 400 `slot-full` / `model-duplicate` |
| AC-218 | 409 `profile-limit-reached` on draft and create; `customProfileLimit` |
| AC-219 | 409 `system-profile-read-only` on PUT / DELETE |
| AC-220 | `deleteModelProfile` 200 `defaultReset: true` |
| AC-221 | 400 `model-left-catalog`; `duplicatedFrom`; older missing models kept |
| AC-222 | 404 `not-found` on get / update / delete / default / draft `from` / `duplicatedFrom` |
| AC-10 | `Slot.state = fallback`, `currentModelId`, price from it; `public-api.md` `MISSING` skip |
| AC-223 | `Slot.state` `no-model-available` / `not-used`, `choosable: false`, 409 `no-text-model`; port `NO_MODEL_AVAILABLE` |
| AC-224 | `public-api.md` move-on outcomes, `answeredBy`, `fallback` |
| AC-228 | `public-api.md` stop outcomes, `NO_MODEL_ANSWERED` with attempts |
| AC-229 | `public-api.md` call record + `events.md` `ModelCallFinished` |
| AC-225 | `listModelProfiles` system profiles from settings/overrides; `getModelCatalog` loads with no action |
| AC-226 | `getModelCatalog.state = not-configured`, `aiConfigured: false`, 409 `ai-not-configured`; port `AI_NOT_CONFIGURED` |
| AC-227 | `ChainModel.availability = not-capable` / `not-in-catalog`, `Slot.state = fallback`; `events.md` `ModelCatalogRefreshed` re-validation |
