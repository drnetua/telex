# Tracker — model-profiles

> Status of every task in the epic. `implement` updates `done` as it commits each task.
> States: `todo` · `in_progress` · `blocked` · `review` · `done`.

| # | Task | Layer | Owner | Estimate | Blocked by | Status |
|---|---|---|---|---|---|---|
| T1 | Promote the four staged model-profiles migrations into the live Flyway tree | migration | Anton Husiev | S | — | done |
| T2 | Define the llm catalog value types, the slot-fit rule and the OpenRouter model-list parser | domain | Anton Husiev | M | — | done |
| T3 | Store the catalog snapshot in Postgres, load it at start and hold it in memory | infra | Anton Husiev | M | T1, T2 | done |
| T4 | Refresh the catalog from OpenRouter at start, every 24 h and every 5 min after a failure | app | Anton Husiev | M | T3 | done |
| T5 | Build the in-call fallback loop in llm with one attempt per model, a per-attempt timeout and outcome classification | app | Anton Husiev | M | T2 | done |
| T6 | Call OpenRouter for text, vision and image through the provider port and classify its errors | infra | Anton Husiev | M | T4, T5 | done |
| T7 | Model the custom profile aggregate, ProfileRef and the profile rules in plain Kotlin | domain | Anton Husiev | M | T2 | done |
| T8 | Resolve slots against the current catalog and estimate the price per 100 runs | domain | Anton Husiev | S | T2, T7 | done |
| T9 | Build the three system profiles from settings and validate the Operator's slot overrides | app | Anton Husiev | S | T4, T8 | done |
| T10 | Persist custom profiles, their chains and the default profile, always scoped by Owner | infra | Anton Husiev | M | T1, T7 | done |
| T11 | Serve the catalog view, the profile list with picker data, one profile and a new-profile draft | app | Anton Husiev | M | T3, T9, T10 | done |
| T12 | Create, update and delete custom profiles and choose the default, each in one transaction | app | Anton Husiev | M | T11 | done |
| T13 | Answer profile slot calls through ProfileCalls and record every call without content | app | Anton Husiev | M | T6, T11 | done |
| T14 | Expose the read endpoints: catalog, profile list, one profile and the draft | ports | Anton Husiev | M | T11 | done |
| T15 | Expose the write endpoints for profiles and the default profile with their problem codes | ports | Anton Husiev | M | T12, T14 | todo |
| T16 | Add the models API hooks, the Models route and link, and the Model catalog tab | ui | Anton Husiev | M | — | done |
| T17 | Build the Profiles tab: the profile picker, profile cards, delete and the AI-not-set-up alert | ui | Anton Husiev | M | T16 | todo |
| T18 | Build the profile editor modal with the chain editor and the model chooser | ui | Anton Husiev | M | T17 | todo |
| T19 | Add Playwright end-to-end tests for the Models page at 360 px and 1280 px with axe and a 500-model load timing | tests | Anton Husiev | M | T15, T18 | todo |
| T20 | Document the AI settings in the README and add the real-call smoke check per slot | docs | Anton Husiev | S | T9, T13 | todo |

**Total:** 20 tasks, ~18 person-days.

## Deviations

- **T2 — `pricePerImage` is always null.** OpenRouter's `pricing.image_output` is a price per output image *token*, not per image; neither spec, data-model nor sad names a per-image source. Image-slot prices show "Price unknown" until one is chosen (T2 edge-case table: no identifiable per-image price → null). Models whose output modalities include audio are skipped as unfit.
- **T10 — race test fixed (test bug, not weakened).** The AC-218 race test placed a 2-party barrier *after* the advisory lock, so the holder waited for a thread blocked on that lock (deadlock). The barrier now comes before the lock, and a 300 ms window after the count keeps a lock-less implementation failing.
- **T6 — no Spring AI starter; JDK `HttpClient`.** One hand-built `/chat/completions` path serves text, vision and image (sad §11 already needed a separate image client). `RestClient` debug-logged the request body (prompt) and the JDK HTTP stack the auth header under DEBUG, so the adapter uses `java.net.http.HttpClient`, which logs neither. Error table: 429 rate-limited · 408 timeout · 413 too-large · 404 unavailable · 422 invalid-request · 403 content-refused · 400 by body (context length/too long → too-large; moderation/content policy/flagged → content-refused; else invalid-request) · 502/503 "no endpoints"/"no allowed providers" → unavailable, else provider-error · 401/402 and anything else → provider-error · 200 with `finish_reason: content_filter` → content-refused.
- **T9 — shipped system-profile models** (verified on OpenRouter `/models` 2026-10-03): Fast and cheap `google/gemini-3.5-flash-lite` (text, vision) · `google/gemini-3.1-flash-lite-image` (image); Balanced `google/gemini-3.5-flash` · `google/gemini-3.1-flash-image`; Careful `anthropic/claude-sonnet-5` · `google/gemini-3-pro-image`. `SystemProfileKey.FAST.displayName` is "Fast and cheap" (spec §1); AC-214 reserves both display names and wire keys.
- **T13 — `ProfileCalls` reuses llm's `SlotRequest`/`SlotAnswer`/`Attempt`/`AttemptOutcome`/`ModelId`** instead of agents-side copies (public-api.md lists them under `telex.agents`): only core modules call `ProfileCalls`, `web` never does. `ModelCallId`/`SlotFailure` live in `ProfileCalls.kt`. The `telex.model.attempt` timer records call time ÷ attempts, because `llm` doesn't yet report per-attempt latency.
- **T14 — fixture fixed + contract validation added.** `ModelsReadApiIT`'s Balanced fixture contradicted its own comment (main model was in the catalog yet `fallback` was expected); the fixture now matches the comment. `ContractValidator` takes a spec path, and the models ITs validate every response against this feature's `openapi.yaml`. The validator mis-parses the `oneOf`-typed `from` query param as JSON, so that one request-side key is ignored in this IT only.
