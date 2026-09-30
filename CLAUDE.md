# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

teleX (Gradle root project `tele-x`) is a multi-user web Telegram client where each Owner runs their own AI agents inside their Telegram account. The skeleton exists (materialized by `/sdd:scaffold` from `docs/architecture-map.md`): a Spring Boot app with the 13 empty Modulith modules, the TDLib facade subproject, a React SPA, Flyway baseline, test harness and CI. Features build into it; E01 `platform-skeleton` still owns sign-up / sign-in and the one-command README.

The project is a learning exercise in Spec-Driven Development using the `sdd` Claude Code plugin (enabled in `.claude/settings.json`). Every feature goes through the pipeline `/sdd:classify-size → specify → clarify → ux-flows → design → sequences → data-model → api → screens → tasks → plan-tests → implement → review → ship`; artifacts land in `docs/features/{slug}/`. Specs are the source of truth — don't implement ahead of them, and record deviations. `docs/architecture-map.md` + `docs/adr/` hold the foundation decisions and conventions.

## Docs (in Ukrainian; UI copy is English)

- `docs/docs/01-tech-spec.md` — concept, glossary (canonical role/term names: Owner, Operator, Counterpart, Linked Account, Channel, Agent, Run, Trigger, Scope, Autonomy Level, Approval…), stack, architecture, domain events, NFRs, risks.
- `docs/docs/02-epics.md` — 29 epics (E01–E29) with features, US/AC ids and per-epic DoD, plus the **shared Definition of Done** that applies to every epic. An epic is done only when all its listed features are fully implemented.
- `docs/docs/03-product-spec.md` — personas, use cases UC-xx, flows F1–F6, screen inventory SCR-xx, components C-xx, decisions D-01…D-20.
- `docs/docs/design-system/` — Tabler 1.6-based design system: `README.md` (brand/content/color/a11y rules), `tokens.json`, per-component READMEs + previews. `docs/designs/` holds Claude Design (`*.dc.html`) mockups that only render inside Claude Design.
- `docs/README.md` links the live (authoritative) versions of these docs; the files here are snapshots.

## Build

Gradle Kotlin DSL, wrapper included, configuration cache + build cache + parallel on. JDK 25 toolchain (auto-provisioned via foojay). Frontend needs Node + pnpm (version pinned by `packageManager` in the root `package.json`); integration tests need Docker.

```bash
./gradlew build                 # everything: compile, unit + integration tests, detekt, spotlessCheck, pnpm build + check
./gradlew test                  # backend unit tests only (no Docker) — includes ModularityTest
./gradlew integrationTest       # @SpringBootTest / @ApplicationModuleTest on Testcontainers pgvector (needs Docker)
./gradlew spotlessCheck detekt  # lint; ./gradlew spotlessApply to auto-format (ktlint, Kotlin + .gradle.kts)
./gradlew :backend:app:test --tests 'telex.shared.IdsTest'                        # single unit test class
./gradlew :backend:app:integrationTest --tests 'telex.MigrationRollbackIT'        # single integration test
docker compose up -d && ./gradlew :backend:app:bootRun --args='--spring.profiles.active=local'   # run locally (TELEX_DB_PORT if 5432 is taken)
```

Frontend, inside `frontend/`: `pnpm dev` (Vite, proxies `/api` to :8080), `pnpm run check` (tsc `--noEmit` + ESLint + Prettier check + Vitest), `pnpm run build`. TypeScript stays on 6.x until typescript-eslint supports 7.

If detekt fails with `Could not initialize class com.intellij.ide.plugins.PluginEnabler` right after editing `build-logic`, it is a stale daemon: `./gradlew --stop`.

Versions live only in `gradle/libs.versions.toml` (Boot/Modulith-managed libraries are listed there without a version). Subprojects apply a convention plugin from `build-logic/src/main/kotlin/` rather than configure plugins directly:

- `spring.kotlin.service.conventions` — bootable Spring Boot + Kotlin app.
- `spring.kotlin.module.conventions` — Spring + Kotlin library module (`bootJar` disabled).
- `spring.ai.conventions` / `spring.modulith.conventions` — add the Spring AI / Spring Modulith BOMs.
- `kotlin.detekt.conventions` — adds detekt (+ detektifier) on top of `kotlin.conventions`, with `config/detekt/detekt.yml` over the defaults; the `detekt` task also covers `src/integrationTest`.
- `integration.test.conventions` — the `integrationTest` JVM test suite (`src/integrationTest/kotlin`), inheriting test dependencies; `check` depends on it.
- `kotlin.conventions` always brings Spotless/ktlint and `-Xjsr305=strict`, and compiles `.java` files placed in `src/*/kotlin` (the Modulith `package-info.java`). `.editorconfig` sets `max_line_length = 120` for both ktlint and detekt.
- `pnpm.conventions` — wraps a pnpm package: `assemble` → `pnpm run build`, `check` → `pnpm run check`; depends on the root `:pnpmInstall` (root `build.gradle.kts`, workspace in `pnpm-workspace.yaml`).

Precompiled script plugins can't use the generated `libs` accessors; use `libs.version("alias")` / `libs.coordinates("alias")` from `VersionCatalogExtensions.kt` (these are `internal`, so subproject build scripts keep the generated `libs`).

## Layout and code conventions

- `backend/app` — the one Spring Boot app. Each Modulith module is a direct sub-package of `telex` (`web`, `identity`, `messaging`, `triage`, `agents`, `tools`, `tasks`, `scheduling`, `audit`, `telegram`, `llm`, `decision`, `bot`) with a `package-info.java` declaring `@ApplicationModule(allowedDependencies)`: `web` → core modules; core → core + integration; integration → `shared` only. Public API + events at the module root, everything else in `internal`. `telex.shared` is an OPEN kernel (typed ids, problems) with no Spring beans. `ModularityTest` runs `verify()` and writes module docs to `backend/app/build/spring-modulith-docs`.
- `backend/telegram-tdlib` — Kotlin facade (`telex.telegram.tdlib`) over TDLib; the TDLib binding is its `implementation` dependency, so `org.drinkless.tdlib.*` can't reach `backend/app` (ADR-0002). Empty until the E02 spike.
- `frontend/` — React + TypeScript + Vite + Tabler SPA; `pnpm run build` output is copied into the app's `static/` and served by `telex.web.SpaHosting` (client routes fall back to `index.html`; `/api/**` and missing assets stay 404). UI copy lives in `frontend/src/messages.ts`.
- **IDs:** app-generated UUIDv7 via `telex.shared.Uuid7.next()`, typed per aggregate as `@JvmInline value class XId(override val value: UUID) : TypedId` (ADR-0003).
- **Errors:** RFC 9457 `application/problem+json` with `type = urn:telex:error:<code>`, `code` and `errors[]`; domain errors extend `telex.shared.DomainProblem`, rendered by `telex.web.ProblemHandler`.
- **Migrations:** `backend/app/src/main/resources/db/migration/V<yyyyMMddHHmm>__<name>.sql`, each with a rollback `db/rollback/U<same-version>__<name>.sql`; `MigrationRollbackIT` applies up → down → up for all of them. Feature migrations are staged by `/sdd:data-model` in `docs/features/<slug>/migrations/` and promoted by `implement`.
- **Tests:** `src/test/kotlin` = unit (no Docker); `src/integrationTest/kotlin` = Spring context on Testcontainers (`TestcontainersConfiguration`, `pgvector/pgvector:pg17`). Frontend Vitest next to the component.
- **CI:** `.github/workflows/ci.yml` runs `./gradlew build integrationTest` on JDK 25 + pnpm.

## Target architecture

Spring Boot 4 + Kotlin modular monolith on **Spring Modulith**, 13 modules communicating via Modulith application events (`@ApplicationModuleListener`, event publication registry). Boundaries are enforced by `ApplicationModules.verify()` in tests.

- **Interface:** `web` — REST controllers, SSE stream, React + TypeScript SPA (Vite, TanStack Query, Tabler) built by Gradle and served as static content from Spring Web.
- **Core:** `identity`, `messaging` (Channels, history, pgvector), `triage` (System 1 prefilter + Jev decision), `agents` (Agent, Run, templates, ChatClient), `tools` (tool registry + Scope checks), `tasks` (User Task, Approval, TTL, undo), `scheduling` (per-user cron, Quartz JDBC — ADR-0004), `audit` (append-only log).
- **Integrations (ACL, reached only via ports):** `telegram` (TDLib as user), `llm` (OpenRouter, Model Profiles, Budget), `decision` (TypeSafe Jev via `spring-ai-starter-typesafe`), `bot` (shared Owner Bot via Bot API webhook).

Core principle — **System 1 / System 2: Jev decides, the LLM generates.** Every incoming message goes through a cheap local prefilter and a Jev triage (`Noul`/`Choice`/`Score`); only a confident yes wakes an LLM agent, and low confidence routes to the human review queue rather than refusal.

Architectural rules (constitution candidates):
1. No module other than `telegram` imports `org.drinkless.tdlib.*`.
2. Every tool call is Scope-checked in `tools` code, never in the prompt. Scope, private-zone and consent checks live in code.
3. Every Run has an Owner, a budget and an `audit` record.

Counterpart message text is untrusted input (prompt-injection risk): wrap it, run it through guardrails, and re-check `act`-level actions outside the LLM.

Other stack choices: PostgreSQL + pgvector with Flyway (migrations need rollbacks), passkeys + email magic link auth, envelope encryption per Owner for TDLib session keys and BYOK secrets.

## Quality gates (shared DoD)

- detekt and ktlint: 0 warnings; ESLint and `tsc --noEmit` clean; `ApplicationModules.verify()` green.
- Every AC covered by ≥1 automated test. Test stack: JUnit, `@ApplicationModuleTest`, Testcontainers, WireMock (fakes for OpenRouter and Jev), TDLib behind a port with an in-memory fake, Vitest, Playwright (run at phone and desktop widths).
- Every touched screen works at phone and desktop widths and implements all states from the feature's `screens.md`.
- UI follows `docs/docs/design-system/README.md`: tokens only (no raw hex), `ai` purple only for AI-produced content, status never by color alone, sentence-case English copy with no emoji, all strings in one message catalog.
