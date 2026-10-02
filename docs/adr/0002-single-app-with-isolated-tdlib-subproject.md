---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: []
updated_at: "2026-09-30"
feature_size: "n/a (foundation)"
ticket: "survey greenfield foundation"
---

# 0002 — Keep all 13 Modulith modules as packages in one `backend/app` project, isolate TDLib in a `backend/telegram-tdlib` subproject

- **Status:** Accepted
- **Date:** 2026-09-30
- **Deciders:** Anton Husiev, Claude (survey foundation session)

## Context

Spring Modulith treats each direct sub-package of the application package (`telex.<module>`) as a module and checks boundaries with `ApplicationModules.verify()`, so modules do not need to be separate Gradle projects. One architectural rule is stricter than the others: "no module other than `telegram` imports `org.drinkless.tdlib.*`" (tech-spec §Architecture, rule 1). TDLib is also a native JNI library, the riskiest dependency in the stack.

## Decision drivers

- Rule 1 must hold even when an executor agent "cuts corners".
- Keep the build small and fast for an 8-week project with parallel worktree agents.
- Keep TDLib swappable for an in-memory fake in `@ApplicationModuleTest`.

## Considered options

1. **One `backend/app` with 13 module packages + a `backend/telegram-tdlib` subproject** holding a Kotlin facade over TDLib, which pulls in the TDLib Java binding as an `implementation` dependency.
2. **One Gradle project, all modules as packages** — TDLib on the whole app's classpath; rule 1 checked only by a test or a detekt rule.
3. **One Gradle subproject per module** (13 + an assembling app + a shared API project).

## Decision outcome

**Chosen:** option 1. Because the TDLib binding is an `implementation` dependency of `telegram-tdlib`, `org.drinkless.tdlib.*` is not on `backend/app`'s compile classpath at all: the compiler enforces rule 1. The facade package lives in the `telegram` module's namespace (`telex.telegram.tdlib`), so `verify()` keeps other modules from reaching it. Every other boundary is checked by `verify()`. Option 3 adds 14+ build files and a shared-API project for events with no benefit beyond what `verify()` gives.

## Consequences

**Positive**
- Rule 1 is a compile error, not a code-review finding.
- One Spring Boot app, one `bootJar`, one context — simple tests and startup.
- The native library build (Docker) is scoped to one subproject.

**Negative**
- Other module boundaries are enforced by a test, not the compiler: `ModularityTest` must stay in the unit gate.
- The `telegram` ACL is split in two places: port + adapter in `backend/app` (`telex.telegram`), facade in `backend/telegram-tdlib`.

**Neutral**
- Moving a module into its own subproject later is mechanical because modules only talk through events and public APIs.

## Links

- Tech spec: [[../docs/01-tech-spec.md]] §Architecture
- Architecture map: [[../architecture-map.md]] §Module inventory
- Related ADR: [[0001-kotlin-spring-modulith-postgres-react-stack]]
