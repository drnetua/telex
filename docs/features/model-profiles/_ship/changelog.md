# Changelog — model-profiles

## model-profiles — model catalog with prices, Model Profiles with per-slot fallback, and a default profile

**What:** Owners now have a Models page, reached from Settings → Models (`/settings/models`). It has two tabs.

- **Model catalog.** Lists every OpenRouter model that fits a slot: text, vision or image. Each model shows what it accepts and produces, its price per 1M input and output tokens, and its context size. Owners can search by name and filter by slot. A line shows when the list was last updated.
- **Profiles.** Lists the three system profiles (Fast and cheap, Balanced, Careful) and the Owner's own profiles. Each profile shows an estimated price per 100 runs.
  - The Owner picks a default profile in the profile picker (C-22). A new Owner starts on Balanced.
  - An Owner can create, duplicate, edit and delete up to 20 custom profiles. Each slot holds a Fallback Chain of up to three capable models, which can be reordered.
  - When a slot's main model leaves the catalog, the profile says "Main model unavailable. Using … for now." and teleX uses the next model. When the main model returns, teleX uses it again.

Behind the page there is a profile call port, `ProfileCalls`. It answers a slot through its chain:
- It moves on to the next model in the same request when a model is missing, errors, is rate-limited, times out (60 s) or can't take a request that large.
- It stops at a content refusal or an invalid request.
- It records every call without any content: the models tried, their outcomes, the model that answered, and whether a fallback happened. Run details and the fallback KPI will read this record from E14 on.

Nothing in E10 itself calls a model. E14 (agent runtime) is the first caller.

**Why:** the model market changes under teleX. Models come and go, and prices move. An Owner needs a short choice with a price attached, and AI work must not stop because one model vanished ([spec](../spec.md) §1, §2). Key decisions:

- [ADR-0001](../adr/0001-model-choice-ships-before-agents.md): the model choice ships before agents exist. The picker and the "main model unavailable" warning live on the Models page for now. E09 embeds them in the builder and the assistant card.
- [ADR-0002](../adr/0002-profiles-in-agents-llm-thin-acl.md): profiles live in the `agents` core module. `llm` stays a thin OpenRouter ACL that holds the catalog.
- [ADR-0003](../adr/0003-client-side-fallback-loop-in-llm.md): fallback is a client-side loop in `llm`, with one attempt per model and a per-attempt timeout. It is not OpenRouter's server-side routing.
- [ADR-0004](../adr/0004-call-records-in-agents-table.md): call records go in an `agents`-owned table, written in the call path. `ModelCallFinished` is published in the same transaction.
- [ADR-0005](../adr/0005-system-profiles-in-configuration-by-key.md): system profiles are defined in configuration and referenced by key. Custom profiles are referenced by id.

**How to use:**
- **Operator:** set `TELEX_OPENROUTER_API_KEY` and start teleX. The catalog loads at start and refreshes every 24 h, retrying every 5 min after a failure. The last catalog survives restarts. Optional overrides of a system profile's slots go in `telex.models.system-profiles.<fast|balanced|careful>.<text|vision|image>`. See the [README](../../../../README.md) "AI settings".
- **Owner:** Settings → Models.
- **API:** see [openapi.yaml](../contracts/openapi.yaml):
  - `GET /api/v1/models/catalog`
  - `GET/POST /api/v1/models/profiles`
  - `GET/PUT/DELETE /api/v1/models/profiles/{profileKey}`
  - `GET /api/v1/models/profile-draft[?from=]`
  - `PUT /api/v1/models/default-profile`

  The event `ModelCallFinished` is described in [events.md](../contracts/events.md).

**Operational notes:**
- **Migrations:** four Flyway migrations are applied automatically on startup:
  - `V202610031200__create_model_catalog`
  - `V202610031201__create_model_profile`
  - `V202610031202__create_default_model_profile`
  - `V202610031203__create_model_call`

  Each has a matching `db/rollback/U…` script. `MigrationRollbackIT` proves up → down → up.
- **Config:** `TELEX_OPENROUTER_API_KEY` is the only secret. It is never returned to the browser or logged.
  - Without the key teleX runs normally and logs a startup WARN naming the variable. Owners see "AI models aren't set up" on the Models page.
  - Tunables: `telex.llm.attempt-timeout` (60 s), `telex.llm.catalog.refresh-interval` (24 h), `telex.llm.catalog.retry-interval` (5 min) and `telex.llm.openrouter.base-url`.
  - A system-profile override that names a model missing from the catalog or unable to do the slot's job is logged as a WARN and skipped.
- **Rollback:** revert the deploy, then apply `U202610031203` … `U202610031200` in reverse order. This drops the catalog snapshot, custom profiles, default-profile choices and call records.

**Acceptance criteria delivered:** AC-51, AC-210, AC-211, AC-212, AC-213, AC-214, AC-215, AC-216, AC-217, AC-218, AC-219, AC-220, AC-221, AC-222, AC-10, AC-223, AC-224, AC-228, AC-229, AC-225, AC-226, AC-227 (all 22 in spec §5).

**Spec deviations (recorded in spec §1):**
- The picker and the fallback warning live on the Models page. E09 embeds them in the builder and the assistant card.
- Choosing a profile sets the Owner's default profile.
- E10 adds a Models page to host SCR-34.
- System profile names follow the mockup.
- The per-slot price ceiling is dropped. Budgets in E27 cap spending.
- Image models are priced per 1M tokens, because OpenRouter publishes no per-image price.
- The real call per slot is a developer smoke check (`RealProviderSmokeIT`), not a UI button.
- Rebased on E06: the interim header link is gone. Models sits under Settings inside the AppShell.

**Known, deferred (spec §8, owner Anton Husiev, before `/sdd:specify agent-builder`):**
- B1: duplicating a system profile whose override names an incapable model is refused on save.
- B2: the suggested "… copy N" name can exceed 40 characters.
- B3: when the server refuses a model on save, the editor shows generic copy.
