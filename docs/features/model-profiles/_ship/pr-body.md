## Summary

This PR ships E10: a Models page with the OpenRouter Model Catalog (prices per 1M tokens, filter by slot) and the Owner's Model Profiles.

- **Profiles:** three system profiles plus up to 20 custom ones. Each has text, vision and image slots, and each slot holds a Fallback Chain of up to three models.
- **Default profile:** the Owner chooses it in the profile picker (C-22). Every option shows an estimated price per 100 runs.
- **Missing models:** when a slot's main model leaves the catalog, the profile says so and uses the next model.
- **Calls:** the `ProfileCalls` port answers a slot through its chain, falling back within the same request. It records every call without content. E14 is its first caller.

The branch is rebased on `master`, which now includes E06 app-shell. `PageFrame` is gone, so the Models page renders inside the AppShell and is reached from Settings → Models. See "Rebase on app-shell" below.

- Spec: [`docs/features/model-profiles/spec.md`](docs/features/model-profiles/spec.md)
- Changelog: [`docs/features/model-profiles/_ship/changelog.md`](docs/features/model-profiles/_ship/changelog.md)

## Acceptance criteria

- AC-51 — each picker option shows a price per 100 runs; switching the default to Careful confirms it by name ✓
- AC-210 — "Price unknown", "Free" and "< $0.01 per 100 runs" states; a profile without a price can still be chosen ✓
- AC-211 — catalog search and slot filter; each model shows name, provider, takes/produces, price per 1M tokens, context and "Updated …"; only slot-fitting models are listed ✓
- AC-212 — a failed refresh keeps the last list, with a note and its time, also across restarts; on a first start the list "isn't available yet" and retries every 5 min ✓
- AC-213 — duplicating Balanced gives "Balanced copy" / "copy 2", with reordered chains, the image slot "Not used" and "Profile saved"; missing models are copied and marked ✓
- AC-214 — the name rules: empty, more than 40 characters, taken (ignoring case), a system name ✓
- AC-215 — the text slot needs at least one model ✓
- AC-216 — a model that can't do the slot's job is refused, with slot-specific words ✓
- AC-217 — at most three models per slot, each only once ✓
- AC-218 — 20 custom profiles at most; create and duplicate are both refused ✓
- AC-219 — system profiles can't be changed or deleted; the Owner is offered Duplicate ✓
- AC-220 — deleting the default custom profile puts the default back to Balanced, and the Owner is told ✓
- AC-221 — a newly added model that left the catalog is refused; an older missing one is kept and marked ✓
- AC-222 — another Owner's profile is invisible; its link gives the same not-found page as a missing profile ✓
- AC-10 — "Main model unavailable. Using B for now.", with the price from B; calls go to B; A is used again when it returns ✓
- AC-223 — a slot with no model, or "Not used"; the call fails plainly; a profile with no text model can't become a new default ✓
- AC-224 — in-call fallback on missing, provider error, rate limit, timeout or a request too large; the next request starts from A ✓
- AC-228 — no further models after a content refusal or an invalid request; the error lists every model tried ✓
- AC-229 — every call is recorded without content: models tried with outcomes, the answering model and the fallback flag ✓
- AC-225 — the catalog loads on start with a key; Operator overrides apply exactly ✓
- AC-226 — without a key teleX runs; the Owner sees the banner and no models; the log has a startup WARN ✓
- AC-227 — a bad override is logged, skipped, and shows the fallback warning ✓

## Design

- Spec: `docs/features/model-profiles/spec.md` (deviations in §1; open B1–B3 in §8)
- Architecture: `docs/features/model-profiles/sad.md`
- Decisions: `docs/features/model-profiles/adr/` (ADR-0001…0005, all Accepted)
- Data model + migrations: `docs/features/model-profiles/data-model.md` (`V202610031200`…`V202610031203`)
- API: `docs/features/model-profiles/contracts/openapi.yaml` (+ `events.md`, `public-api.md`)
- UX: `ux-flows.md`, `screens.md`
- Review: `_review/review-2026-10-03.md` (CHANGES REQUESTED) → T21–T29 → `-r2` (CHANGES REQUESTED) → T30 → `-r3` **PASS**

## Tasks (SDD-Task trailers)

- **Feature, T1–T20.** These commits predate the trailer and map 1:1 to `tasks.json`.
  - ab7f1ed T1 promote migrations
  - dec1f1d T2 catalog value types, slot fit, parser
  - 1ef3a2c T3 catalog snapshot store
  - 953408e T4 catalog refresh schedule
  - 9796d30 T5 fallback loop
  - c14d64d T6 OpenRouter provider
  - 2d1a8a2 T7 profile aggregate + rules
  - a36d6e7 T8 slot resolution + price estimate
  - 5deb5f1 T9 system profiles + override validation
  - 71b89af T10 profile persistence
  - 013bed7 T11 read services
  - 064c6b2 T12 write services
  - cd93fef T13 ProfileCalls + call records
  - 0d93674 T14 read endpoints
  - 8076f91 T15 write endpoints
  - a684b21 T16 Models route + catalog tab
  - f3b3060 T17 Profiles tab
  - 2f14015 T18 profile editor
  - a86056c T19 Playwright e2e
  - 886348f T20 README + real-call smoke check
- **Review fixes, T21–T30.**
  - 8d2af34 T21
  - 6ff6b2f T22
  - 0f55092 T23
  - 61adb84 T24
  - fb0008f T25
  - dbb775c T26
  - da3a817 T27
  - baa3fdd T28
  - cc542f5, 5e4bbce T29
  - be2b3d7, c6e5ab0 T30
- **Ship.**
  - df4bb90 move the Models page into the app shell
  - ccae5c6 test isolation fix after the rebase

## Rebase on app-shell

- **Frame:** `PageFrame` was deleted by E06. It is kept through the rebased feature commits and removed by the app-shell integration commit. `ModelsPage` now wraps itself in the new `AppFrame` (the signed-in frame `AppLayout` uses), so SCR-91 for a foreign or missing profile still renders bare.
- **Navigation:** the interim header link is replaced by a Settings row "Models" (`settingsRegistry.ts`).
- **Fetches:** the catalog and profile queries moved inside the frame, so opening the page requests the catalog once.
- **Tests:** the Models Vitest suites answer the shell's `/me` and `/api/v1/pulse` calls and reset the shell's connectivity state between tests.
- **Test isolation:** `ProfileRepositoriesIT` now clears its profile rows. app-shell's `OwnerPreferencesIT` shares its Spring context and deletes every Owner, which failed on the foreign keys.
- **Shared files:** CI, `compose.e2e.yaml` (e2e profile + WireMock OpenRouter) and `ContractValidator` carry both features' additions, resolved in T14 and T19, the commits that introduced them.

## Verification

- **Gate before the rebase (the review-PASS head, same feature code):** `./gradlew build integrationTest` green.
- **Gate at the rebased head:** `./gradlew build integrationTest` green: 188 unit tests, and 306 integration tests with 1 skipped (`RealProviderSmokeIT`, no key). detekt, spotless, ModularityTest and pnpm build + check are clean. The tree is the same as the merge-based head the Playwright suite ran against, apart from the order of two message-catalog blocks.
- **Frontend at the rebased head:** `pnpm run check` green: tsc, ESLint, Prettier, Vitest 439/439.
- **Playwright:** the full suite against the rebased stack (`compose.yaml` + `compose.e2e.yaml`) passed 114/114 at 360 px and 1280 px, including `models.spec.ts` (axe, 500-model load timing) and the app-shell specs.
- **Ran the feature:** the rebased stack with the WireMock OpenRouter (500 models), with two Owners signed in through Mailpit codes, over HTTP:
  - **AC-225:** the catalog loaded on start with no action (`current`, 499 slot-fitting models); all three system profiles were listed.
  - **AC-211:** each model has a price per 1M tokens, a context size and its slots; the vision filter set has 105 models.
  - **AC-51 / price:** Balanced shows ≈ $0.22 per 100 runs (100 × (3,000 × $0.30 + 500 × $2.50) / 1M). `PUT default-profile` Careful → 200, and the list then shows Careful as default.
  - **AC-213:** the draft from Balanced was named "Balanced copy", and copied Balanced's missing image model marked `not-in-catalog`. "Cheap vision" was saved (201) with the text chain in the chosen order and the image slot `not-used`.
  - **AC-214:** each name rule gave 400 with its field code: `name-required`, `name-too-long`, `name-taken` (ignoring case), `name-reserved`.
  - **AC-215 / AC-216 / AC-217 / AC-221:** 400 with `text-slot-required`, `model-not-capable` on `slots.vision[0]`, `slot-full` on `slots.text[3]`, `model-duplicate`, and `model-left-catalog`.
  - **AC-218:** 20 creates returned 201; the 21st create and a duplicate draft both returned 409 `profile-limit-reached`.
  - **AC-219:** PUT and DELETE on `balanced` returned 409 `system-profile-read-only`.
  - **AC-222:** the second Owner's list doesn't contain the first Owner's profile. GET, DELETE and choose-as-default on it return 404 `not-found`, with the same body as a random id.
  - **AC-220:** after making the custom profile the default and deleting it, the reply was `defaultReset: true` and the default was Balanced.
  - **AC-223 / AC-227:** the fixture lacks Balanced's shipped image model, so that slot shows `no-model-available`. Balanced stays choosable because its text slot has a model, and a WARN names the profile, slot and model.
  - **AC-10:** with model A removed from the WireMock catalog and the app restarted, the profile's text slot was in `fallback` on B, with A `not-in-catalog`. With A restored and the app restarted, the slot was back to `main-model` A.
  - **AC-212:** with `/models` returning 503 and the app restarted, the catalog was `update-failed` with the earlier list (499 models) and timestamps. Profiles still resolved, an edit saved (200), and the log showed "refresh failed … keeping the last snapshot".
  - **AC-226:** restarted without the key, the startup log says "No model provider key configured: set TELEX_OPENROUTER_API_KEY …". `aiConfigured: false`, every profile shows no model available and can't be chosen, the catalog is `not-configured`, edits return 409 `ai-not-configured`, and `/me` and `/sessions` still answer 200.
  - **Covered by integration tests only:** AC-224, AC-228 and AC-229 have no HTTP caller before E14. They are covered by `ProfileCallsIT`, `ProfileCallsStalledIT` and `FallbackLoop` tests against the WireMock provider.
- **Real call per slot (E10 DoD, spec §6):** **not run here.** No OpenRouter key was available in this environment, so `RealProviderSmokeIT` was skipped. Run it before merging and paste the output below:

  ```bash
  export TELEX_OPENROUTER_API_KEY=<key>
  ./gradlew :backend:app:integrationTest --tests 'telex.agents.RealProviderSmokeIT'
  ```

## Operational notes

- **Migrations:** `V202610031200__create_model_catalog`, `V202610031201__create_model_profile`, `V202610031202__create_default_model_profile` and `V202610031203__create_model_call` are applied on startup. Each has a `db/rollback/U…` script, and `MigrationRollbackIT` covers up → down → up.
- **Rollback:** revert the deploy and apply the `U…` scripts in reverse order. This drops the catalog snapshot, custom profiles, defaults and call records.
- **Config:** `TELEX_OPENROUTER_API_KEY` (secret, optional; without it AI is off and a WARN is logged). Tunables: `telex.llm.attempt-timeout`, `telex.llm.catalog.refresh-interval` / `retry-interval`, `telex.llm.openrouter.base-url` and `telex.models.system-profiles.*` overrides. See README "AI settings".

🤖 Generated with [Claude Code](https://claude.com/claude-code)
