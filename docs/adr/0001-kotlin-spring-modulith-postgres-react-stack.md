---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: []
updated_at: "2026-09-30"
feature_size: "n/a (foundation)"
ticket: "survey greenfield foundation"
---

# 0001 — Build teleX on Kotlin + Spring Boot 4 + Spring Modulith, PostgreSQL + pgvector, React SPA

- **Status:** Accepted
- **Date:** 2026-09-30
- **Deciders:** Anton Husiev, Claude (survey foundation session)

## Context

teleX is a multi-user web Telegram client: each Owner links Telegram accounts (as a user, via TDLib) and runs their own AI agents. Every incoming message goes through a cheap System 1 triage (Jev) before an expensive System 2 LLM agent (OpenRouter) wakes up. It is an 8-week learning project on the SDD pipeline, demoed with one `docker compose up`. The stack was fixed in `docs/docs/01-tech-spec.md` §Tech stack; this ADR records it as the foundation.

## Decision drivers

- Hundreds of concurrent TDLib callbacks and LLM calls per instance (NFR-04: ≥ 50 Linked Accounts on 4 vCPU / 8 GB).
- Module boundaries that agent-executors cannot silently cross (risk: "agents drift from specs").
- First-class AI tooling: `ChatClient`, tools, advisors, `VectorStore`, and the Jev starter (`spring-ai-starter-typesafe`).
- One datastore for domain data, embeddings and durable events (NFR-06: no event loss after restart).
- A single deployable plus one Postgres, so the demo starts with one command.

## Considered options

1. **Kotlin 2.4 on JDK 25 + Spring Boot 4.1 + Spring Modulith + Spring AI; PostgreSQL + pgvector; React + TypeScript + Tabler SPA served by Spring Web** — a modular monolith.
2. **Microservices per bounded context** — each module deployed separately with a broker.
3. **Node/TypeScript full-stack** — one language across front and back.

## Decision outcome

**Chosen:** option 1. Virtual threads cover the concurrency driver, `ApplicationModules.verify()` makes boundaries a test the executors must pass, Spring AI + the Jev starter cover the AI drivers natively, and Postgres + pgvector + the Modulith JDBC event registry keep everything in one database. Option 2 multiplies infrastructure for a single-developer 8-week project. Option 3 has no maintained TDLib user-client binding on the level of the official Java interface and no Jev starter.

## Consequences

**Positive**
- One deployable and one database: `docker compose up` is enough for the demo.
- Boundaries are machine-checked on every build.
- Jev guardrail/judge advisors and OpenRouter work through one Spring AI abstraction.

**Negative**
- TDLib JNI must be built for JDK 25 (tech-spec risk; spike in E02).
- Two toolchains (Gradle + pnpm) in one build.
- Bleeding-edge versions (Boot 4.1, Spring AI 2.0, detekt 2.0 alpha) mean fewer answered questions online.

**Neutral**
- A module can be extracted into a service later, because modules already talk through events.

## Links

- Tech spec: [[../docs/01-tech-spec.md]] §Tech stack, §Architecture
- Architecture map: [[../architecture-map.md]] §Stack
- Related ADR: [[0002-single-app-with-isolated-tdlib-subproject]], [[0003-postgres-jdbc-flyway-uuidv7-persistence]]
