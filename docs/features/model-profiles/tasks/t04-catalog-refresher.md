---
id: T4
title: "Refresh the catalog from OpenRouter at start, every 24 h and every 5 min after a failure"
layer: "app"
deps: ["T3"]
blocks: ["T6", "T9"]
acs: ["AC-212", "AC-225", "AC-226"]
files_hint: ["backend/app/src/main/kotlin/telex/llm/internal/catalog/", "backend/app/src/main/kotlin/telex/llm/internal/openrouter/OpenRouterProperties.kt", "backend/app/src/main/kotlin/telex/llm/internal/openrouter/OpenRouterModelsClient.kt", "backend/app/src/main/kotlin/telex/llm/ModelCatalogRefreshed.kt", "backend/app/src/main/resources/application.yaml", "gradle/libs.versions.toml", "backend/app/build.gradle.kts", "backend/app/src/integrationTest/kotlin/telex/llm/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T4 — Refresh the catalog from OpenRouter at start, every 24 h and every 5 min after a failure

## Place in the sequence

- **Blocked by:** T3 — Store the catalog snapshot in Postgres, load it at start and hold it in memory · **Blocks:** T6 — Call OpenRouter for text, vision and image through the provider port and classify its errors, T9 — Build the three system profiles from settings and validate the Operator's slot overrides · **Wave:** 3 — needs the snapshot store (T3).
- **Lane:** shares `internal/catalog/` with T3 and `application.yaml` / the version catalog with T6 and T9 — serialized.

## Why (user story)

> **As an** Owner
> **I want** to see the models teleX can use, with what each one can do and what it costs
> **So that** I can tell which models suit text, photos or images before I build a profile
>
> — `spec.md §4, US-80, verbatim` · full text: [spec.md](../spec.md)

> **As an** Operator
> **I want** to set the installation's provider key, and optionally override the default models behind the three system profiles, in the installation settings
> **So that** every Owner gets working profiles without configuring anything
>
> — `spec.md §4, US-83, verbatim` · full text: [spec.md](../spec.md)

This task keeps the Model Catalog fresh without anyone's action and makes a provider outage or a missing key harmless.

## Inlined context

> Sched->>Llm: refresh at start and every 24 h
> alt no provider key configured: logs a startup warning naming the missing setting and stays not configured
> else key configured: fetches the model list
>   alt provider reachable: keeps only models that fit a slot → replaces the snapshot and its refresh time → publishes the catalog refreshed event
>   else provider down: records the failed attempt and keeps the last snapshot → schedules a retry in 5 minutes
>
> — `sad.md §6, Critical flow 2 (catalog refresh and outage), abridged` · full text: [sad.md](../sad.md)

> **New installation settings:** `TELEX_OPENROUTER_API_KEY` → `telex.llm.openrouter.api-key` (required for AI; its absence is a startup WARN, not a failure, AC-226). `telex.llm.openrouter.base-url` defaults to `https://openrouter.ai/api/v1`. `telex.llm.attempt-timeout` defaults to `60s`. `telex.llm.catalog.refresh-interval` defaults to `24h` and `telex.llm.catalog.retry-interval` to `5m`.
> **Scheduling:** Spring `@Scheduled` inside the app (`@EnableScheduling`). There is no Quartz until E20. Refresh is idempotent (it replaces the snapshot).
> **Local:** `docker compose up` works without a key, showing the "AI models aren't set up" banner. Tests never reach OpenRouter: WireMock serves `/models` and the chat and image endpoints.
> **Metrics:** gauge `telex.llm.catalog.age.hours`, counter `telex.llm.catalog.refresh{outcome=ok|failed}`, gauge `telex.llm.catalog.models`. **Logs:** WARN on a missing key at startup, on each failed refresh […]. INFO on each successful refresh with the model count.
>
> — `sad.md §7, Deployment view, abridged` · full text: [sad.md](../sad.md)

> Kotlin type `telex.llm.ModelCatalogRefreshed`. `data`: `refreshedAt` (model_catalog_state.last_refreshed_at), `modelCount` (rows after the replace). Producer: `llm` catalog refresher, in the transaction that replaces the snapshot. **Not published** on a failed refresh or when no key is configured.
>
> — `events.md §Event llm.model-catalog-refreshed.v1, abridged` · full text: [events.md](../contracts/events.md)

> | Catalog freshness | refreshed at start and every 24 h, retried every 5 min after a failure; age ≤ 25 h while the provider is reachable; the last catalog survives a restart | integration test with a controlled clock and a restart |
> | Provider outage | 0 failed Models page loads, profile saves and slot resolutions while the provider is down; the last known catalog stays in use | integration test with the provider fake down |
>
> — `spec.md §6, NFR rows Catalog freshness + Provider outage, verbatim` · full text: [spec.md](../spec.md)

> | Configuration | `@ConfigurationProperties` under `telex.llm.*` (provider, timeouts, refresh) and `telex.models.system-profiles.*` (defaults in `application.yaml`, overrides by environment). These are the first typed properties classes in the repo | feature ADR-0005 |
>
> — `sad.md §8, Configuration, verbatim` · full text: [sad.md](../sad.md)

> Data classification internal. teleX never stores or logs request or answer text (spec §6.1). A profile call passes the caller's request only to the model provider and returns the answer to the caller, and the call record is content-free by construction (AC-229). Nothing an Owner does in E10 triggers a model call.
>
> The provider key is an installation secret. It is set only through installation settings (environment), never returned to the browser, never logged (spec §6.1).
>
> — `sad.md §2, Regulatory / external, abridged` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes. Writes `model_catalog_entry` / `model_catalog_state` through T3's store only.

## API contract

Internal — no HTTP surface of teleX. Outbound: `GET {telex.llm.openrouter.base-url}/models` with the installation key, parsed by T2's `ModelListParser`. Publishes `ModelCatalogRefreshed(refreshedAt, modelCount)` at the `llm` root.

— `events.md, llm.model-catalog-refreshed.v1, abridged` · full text: [events.md](../contracts/events.md)

## Acceptance criteria

### AC-212 — error (US-80)

> **Given** the Model Catalog loaded earlier, and its latest automatic refresh has failed
> **When** the Owner opens the catalog
> **Then** the Owner sees the last known list with a note that it couldn't be updated and the time it is from, and profiles keep working with that list, also after teleX restarts; on a first start with no list ever loaded, the Owner sees "The model list isn't available yet", every slot shows no model available, and teleX retries every 5 minutes
>
> — `spec.md §5, AC-212, verbatim` · full text: [spec.md](../spec.md)

### AC-225 — happy (US-83)

> **Given** an Operator who has set the installation's provider key as the README describes, and has either left the system profiles' models alone or overridden the models of Balanced in the installation settings
> **When** the Operator starts teleX
> **Then** the Model Catalog loads without further action, and every Owner sees the three system profiles: with the models that come with teleX where nothing was overridden, and with exactly the Operator's models for Balanced where it was
>
> — `spec.md §5, AC-225, verbatim` · full text: [spec.md](../spec.md)

### AC-226 — error (US-83)

> **Given** an installation started without a provider key
> **When** an Owner opens the Models page
> **Then** teleX works otherwise (sign-in and everything without AI), the Owner sees a banner saying that AI models aren't set up on this installation yet and to ask the person who runs it, the three system profiles are listed with no model available, custom profiles can't be edited, and the Operator finds a startup warning naming the missing setting in the teleX log
>
> — `spec.md §5, AC-226, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Add WireMock (test scope) to `gradle/libs.versions.toml` + `backend/app/build.gradle.kts` — versions only in the catalog
- [ ] `OpenRouterProperties` (`telex.llm.openrouter.*`) and `LlmProperties` (`attempt-timeout`, `catalog.refresh-interval`, `catalog.retry-interval`) with defaults in `application.yaml`; key from `TELEX_OPENROUTER_API_KEY` — `backend/app/src/main/kotlin/telex/llm/internal/openrouter/`
- [ ] `OpenRouterModelsClient` (RestClient, bearer key, short connect/read timeouts) — `backend/app/src/main/kotlin/telex/llm/internal/openrouter/`
- [ ] `CatalogRefresher`: refresh on start, next run after `refresh-interval` on success or `retry-interval` on failure (driven by the injected `Clock` / a `TaskScheduler`), WARN on missing key, metrics, `ModelCatalogRefreshed` published in the replace transaction — `backend/app/src/main/kotlin/telex/llm/internal/catalog/`
- [ ] Integration tests with WireMock + a controllable clock: start refresh, +24 h, fail → +5 min retry, restart with the fake down keeps the snapshot, no key → WARN and no fetch — `backend/app/src/integrationTest/kotlin/telex/llm/`

## Edge cases

| Case | Behaviour |
|---|---|
| No provider key | No fetch, no DB write, startup WARN naming `TELEX_OPENROUTER_API_KEY`, state `not-configured` |
| Provider returns 5xx or times out | `last_failed_at` set, previous snapshot kept, retry in 5 min, WARN logged |
| Provider returns an empty or unparsable list | Treated as a failed refresh — never replace a good snapshot with nothing |
| Retries keep failing | Retry every 5 min with no limit (sad §6 flags: intended) |
| Key value in logs or metric tags | Never — not in exceptions, URLs or WARN text |

## Definition of Done

- [ ] the refresh/outage/restart integration tests pass with WireMock and a controlled clock
- [ ] the app still boots and `ApplicationSmokeIT` passes without a key
- [ ] every Hard Rule inlined above still holds (no key in logs)
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
