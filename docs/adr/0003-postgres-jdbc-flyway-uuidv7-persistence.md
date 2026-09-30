---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: []
updated_at: "2026-09-30"
feature_size: "n/a (foundation)"
ticket: "survey greenfield foundation"
---

# 0003 — Persist with Spring Data JDBC on PostgreSQL, Flyway migrations with tested rollback scripts, app-generated UUIDv7 ids

- **Status:** Accepted
- **Date:** 2026-09-30
- **Deciders:** Anton Husiev, Claude (survey foundation session)

## Context

Every module stores Owner-scoped data; `messaging` also stores embeddings. The shared Definition of Done requires migrations with rollback, and `/sdd:data-model` produces paired `*.up.sql` / `*.down.sql` files. Community Flyway has no `undo` command. Modulith events are published in the same transaction as the aggregate is saved, so ids are needed before insert. Changing the id format or migration tool after data exists means migrating every table and FK.

## Decision drivers

- DoD: "migrations have rollback".
- NFR-06: events survive restart (JDBC event publication registry in the same DB).
- Multi-user privacy: ids must not reveal volumes or be guessable in URLs.
- Minimal tooling beyond what Spring Boot supports out of the box.

## Considered options

1. **Flyway forward scripts + paired rollback scripts in `db/rollback`, verified by an up → down → up Testcontainers test.**
2. **Liquibase SQL changelogs with built-in `--rollback` blocks.**

For ids: **UUIDv7 generated in the app** (chosen), `bigint identity` from the DB, or TSID (64-bit time-sorted).

## Decision outcome

**Chosen:** option 1, with UUIDv7 ids. Flyway ships with Spring Boot, migrations stay plain SQL, and the rollback test makes rollback a checked property rather than a promise. The tech spec and epics already say Flyway. UUIDv7 is known before insert (events, idempotency), time-sorted (B-tree friendly), and not enumerable. `bigint identity` leaks volumes and is only known after insert. TSID needs string encoding in JSON because of JavaScript's 2^53 limit.

Data access is Spring Data JDBC, one repository per aggregate inside the owning module; pgvector lives in the same database.

## Consequences

**Positive**
- Rollback exists for every migration and CI proves it.
- One database for domain data, embeddings, Modulith events and Quartz.
- Ids can be assigned in the domain layer and are safe to expose.

**Negative**
- Rolling back a live DB is manual: run the `U…` script, then delete the row from `flyway_schema_history`.
- 16-byte keys instead of 8; long ids in URLs.
- Every migration PR carries two files that must stay in sync.

**Neutral**
- Postgres 18 also has `uuidv7()`; the app-side generator can switch to it later without changing the format.

## Links

- Tech spec: [[../docs/01-tech-spec.md]] §Tech stack
- Epics: [[../docs/02-epics.md]] §Shared Definition of Done
- Architecture map: [[../architecture-map.md]] §Conventions (IDs, Persistence, Migrations)
- Related ADR: [[0001-kotlin-spring-modulith-postgres-react-stack]]
