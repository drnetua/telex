---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: []
updated_at: "2026-09-30"
feature_size: "n/a (foundation)"
ticket: "survey greenfield foundation"
---

# 0004 — Run per-Owner schedules on Quartz with the JDBC job store

- **Status:** Accepted
- **Date:** 2026-09-30
- **Deciders:** Anton Husiev, Claude (survey foundation session)

## Context

Schedule triggers (e.g. the 08:00 digest in the Owner's time zone, Friday task summaries) are per Owner, created at runtime, and must survive restarts. `@Scheduled` cannot do this. The tech spec left "JobRunr or Quartz" open. The scheduler is first needed in E20 (schedules-digest), but the choice was made up front.

## Decision drivers

- Per-Owner cron with a time zone, created and removed at runtime.
- Survives restart without losing or double-firing jobs.
- Runs on the existing Postgres; no new infrastructure.
- Compatible with Spring Boot 4.1 today.

## Considered options

1. **Quartz with the JDBC job store** via `spring-boot-starter-quartz`.
2. **JobRunr** — modern Kotlin-friendly API with a dashboard and retries.
3. **Defer the choice to E20's design.**

## Decision outcome

**Chosen:** option 1. Quartz is supported by Spring Boot itself, so no risk that it lags behind Boot 4.1; time-zone-aware `CronTrigger` covers the per-Owner cron driver; the JDBC store reuses Postgres. JobRunr's nicer API and dashboard don't outweigh the compatibility risk and its paid-tier features.

## Consequences

**Positive**
- No compatibility risk with Spring Boot 4.1.
- Durable, clustered-capable scheduling on the existing database.

**Negative**
- An older, verbose API — wrap it behind the `scheduling` module's own interface.
- 11 `QRTZ_*` tables; created by a Flyway migration (with rollback) in E20, `spring.quartz.jdbc.initialize-schema=never`.

**Neutral**
- The choice is made before E20's requirements are specified; if E20 finds a gap, supersede this ADR there.

## Links

- Tech spec: [[../docs/01-tech-spec.md]] §Tech stack (Scheduler)
- Epics: [[../docs/02-epics.md]] E20 schedules-digest
- Architecture map: [[../architecture-map.md]] §Stack, §Datastores
- Related ADR: [[0003-postgres-jdbc-flyway-uuidv7-persistence]]
