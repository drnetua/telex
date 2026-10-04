---
id: T29
title: "Drop notices for good on navigation, refresh the draft after a save, round prices before the range check, and align the AC-211 docs"
layer: "ui"
deps: ["T26", "T27", "T28"]
acs: ["AC-213", "AC-218", "AC-212", "AC-211"]
files_hint: ["frontend/src/pages/models/ModelsPage.tsx", "frontend/src/pages/models/notice.ts", "frontend/src/pages/models/", "frontend/src/api/models.ts", "backend/app/src/main/kotlin/telex/llm/internal/openrouter/ModelListParser.kt", "backend/app/src/test/kotlin/telex/llm/ModelListParserTest.kt", "docs/features/model-profiles/screens.md", "docs/features/model-profiles/data-model.md", "docs/features/model-profiles/contracts/openapi.yaml", "docs/design-system.md"]
owner: "Anton Husiev"
estimate: "S"
origin: "per-task reviews of T26, T27, T28"
status: "todo"
---

# T29 — Drop notices for good on navigation, refresh the draft after a save, round prices before the range check, and align the AC-211 docs

## Origin

Leftovers from the per-task reviews of T26–T28 (code at `d5450f3`). Upstream: [review-2026-10-03.md](../_review/review-2026-10-03.md), [spec.md](../spec.md) §5, [screens.md](../screens.md).

## Findings to fix

### H1 (blocking, T26 review) — a notice comes back on return to its URL
`frontend/src/pages/models/ModelsPage.tsx:47`: `const notice = entry && entry.url === url ? entry.notice : null` only hides the notice; nothing clears `entry` when the URL changes. Repro: get `409 profile-limit-reached` on `/settings/models/profiles/new` → limit toast on `/settings/models` → click Model catalog (toast hides) → click Profiles (URL is `/settings/models` again) → same toast is back. Same for Edit→Close, choose-default toasts, and save-error notices on `/profiles/new`. **Fix:** key the notice on the history entry (`location.key`) or clear it when the URL/key changes. **Test (RED first):** Vitest catalog → profiles expects no toast; Edit → Close expects no old toast.

### H2 (minor, T26 review) — stale draft reused after a save
`frontend/src/api/models.ts:153`: with `staleTime: 5000` + `refetchOnMount: true`, a draft cached < 5 s is reused on mount; the create mutation only invalidates `["models","profiles"]`. Saving a new profile then going Back to `/settings/models/profiles/new` within 5 s shows the just-used suggested name. **Fix:** invalidate/remove the `["models","profile-draft", …]` keys in the create (and delete) mutation's `onSettled`, keeping the card path to one request. **Test:** Vitest.

### H3 (minor, T28 review) — price range check before rounding
`backend/app/src/main/kotlin/telex/llm/internal/openrouter/ModelListParser.kt:135`: `fitsColumn` compares before Postgres rounds to scale 6; `"99.99999999999999"` per token → `99999999.99999999` < 10^8 passes, the insert rounds to `100000000.000000` and overflows `NUMERIC(14,6)`, failing the refresh. **Fix:** compare `price.setScale(6, RoundingMode.HALF_UP)` (or store the rounded value). **Test:** parser unit test for that value.

### H4 (minor, T27 review) — AC-211 docs drift
- `screens.md:82` SCR-66 default: "Price (input / output per 1M tokens, or per image)" → per 1M tokens only; `:158` W-66e note "Price $0.04 per image" → e.g. "$0.30 in · $30.00 out per 1M tokens".
- `data-model.md:24`: conventions bullet "stored per million tokens and per image, the units the catalog shows (AC-211)" → per-million-token; `price_per_image` reserved (NULL in E10).
- `contracts/openapi.yaml:816` `pricePerImage` description → "reserved; always null in E10"; the `getModelCatalog` example (~:113) for the image model → null `pricePerImage` and per-1M-token prices.
- `docs/design-system.md:53` ModelProfileCard row: add the `no-model-available` slot state (circle-off line with "Pick another model" / "Choose another profile") and the "Not in the catalog" badge.

## Definition of Done

A notice never reappears when the Owner returns to the URL it was raised on; after creating a profile, re-opening Create asks the server for a fresh draft; a per-token price that rounds to 10^8 or more at scale 6 is skipped; screens.md, data-model.md, openapi.yaml and the design-system inventory agree with the amended AC-211 and the built components.

Gate: `cd frontend && pnpm run check`; `./gradlew :backend:app:test spotlessCheck detekt`; openapi.yaml still parses (the models ITs validate against it: `./gradlew :backend:app:integrationTest --tests 'telex.web.ModelsReadApiIT'`).
