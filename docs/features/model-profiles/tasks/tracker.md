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
| T6 | Call OpenRouter for text, vision and image through the provider port and classify its errors | infra | Anton Husiev | M | T4, T5 | todo |
| T7 | Model the custom profile aggregate, ProfileRef and the profile rules in plain Kotlin | domain | Anton Husiev | M | T2 | done |
| T8 | Resolve slots against the current catalog and estimate the price per 100 runs | domain | Anton Husiev | S | T2, T7 | done |
| T9 | Build the three system profiles from settings and validate the Operator's slot overrides | app | Anton Husiev | S | T4, T8 | todo |
| T10 | Persist custom profiles, their chains and the default profile, always scoped by Owner | infra | Anton Husiev | M | T1, T7 | todo |
| T11 | Serve the catalog view, the profile list with picker data, one profile and a new-profile draft | app | Anton Husiev | M | T3, T9, T10 | todo |
| T12 | Create, update and delete custom profiles and choose the default, each in one transaction | app | Anton Husiev | M | T11 | todo |
| T13 | Answer profile slot calls through ProfileCalls and record every call without content | app | Anton Husiev | M | T6, T11 | todo |
| T14 | Expose the read endpoints: catalog, profile list, one profile and the draft | ports | Anton Husiev | M | T11 | todo |
| T15 | Expose the write endpoints for profiles and the default profile with their problem codes | ports | Anton Husiev | M | T12, T14 | todo |
| T16 | Add the models API hooks, the Models route and link, and the Model catalog tab | ui | Anton Husiev | M | — | todo |
| T17 | Build the Profiles tab: the profile picker, profile cards, delete and the AI-not-set-up alert | ui | Anton Husiev | M | T16 | todo |
| T18 | Build the profile editor modal with the chain editor and the model chooser | ui | Anton Husiev | M | T17 | todo |
| T19 | Add Playwright end-to-end tests for the Models page at 360 px and 1280 px with axe and a 500-model load timing | tests | Anton Husiev | M | T15, T18 | todo |
| T20 | Document the AI settings in the README and add the real-call smoke check per slot | docs | Anton Husiev | S | T9, T13 | todo |

**Total:** 20 tasks, ~18 person-days.

## Deviations

- **T2 — `pricePerImage` is always null.** OpenRouter's `pricing.image_output` is a price per output image *token*, not per image; neither spec, data-model nor sad names a per-image source. Image-slot prices show "Price unknown" until one is chosen (T2 edge-case table: no identifiable per-image price → null). Models whose output modalities include audio are skipped as unfit.
