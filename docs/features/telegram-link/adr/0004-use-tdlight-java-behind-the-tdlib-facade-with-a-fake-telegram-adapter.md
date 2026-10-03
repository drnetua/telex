---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E02 telegram-link"
---

# 0004 — Use TDLight Java behind the `telegram-tdlib` facade, with a fake Telegram adapter for tests and local runs

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

teleX signs in to Telegram as a user, which needs Telegram's client library TDLib, a C++ library reached from the JVM through JNI (a native bridge between Java and C++). Nobody has yet shown that TDLib's Java binding works on JDK 25 and ships in the app image (roadmap D1, spec §8 OQ-2). The project's riskiest unknown is here. The developer works on macOS, CI runs on Linux, and the image is `eclipse-temurin:25-jre`. Foundation ADR-0002 already isolates the binding in `backend/telegram-tdlib` as an `implementation` dependency.

## Decision drivers

- Retire roadmap D1 as early and cheaply as possible.
- Architectural rule 1: only the Telegram integration touches the TDLib binding.
- Tests at every level must run without real Telegram or native code (CLAUDE.md: "TDLib behind a port with an in-memory fake").
- A Playwright e2e of the wizard at 360 px and 1280 px (spec §6) needs deterministic codes and errors.

## Considered options

1. **TDLight Java**, a maintained TDLib fork whose native libraries ship prebuilt in Maven for Linux (x64, arm64) and macOS.
2. **Official TDLib built from source** in an extra Dockerfile stage at a pinned tag (`org.drinkless.tdlib`). The developer builds a macOS `dylib` themselves to use real Telegram locally.

## Decision outcome

**Chosen:** option 1. There is no C++ toolchain in the build, and the same artifact works in CI, in the image and on the developer's Mac. The binding stays an `implementation` dependency of `backend/telegram-tdlib`. Only `telex.telegram.tdlib` code imports `it.tdlight.*`, and the rest of `telegram` sees our own facade types. Rule 1 keeps its meaning, but its package name changes. The `telegram` port has two adapters, chosen by `telex.telegram.adapter`:
- `tdlight` (default) wraps the facade;
- `fake` is an in-memory Telegram with fixed codes, a scriptable two-step password, flood waits, banned numbers, session termination and a chat list of a chosen size.

`@ApplicationModuleTest`s, Playwright e2e and `bootRun` without Telegram credentials use `fake`.

The first E02 task is a spike that has to show all of this before any wizard work:
- the natives load on `eclipse-temurin:25-jre` (linux x64) and on macOS;
- a sign-in on Telegram's test servers completes;
- two clients run in one process;
- the image grows by an acceptable amount.

If the spike fails, option 2 is the fallback, and only `backend/telegram-tdlib` and the Dockerfile change.

## Consequences

**Positive**
- D1 shrinks from "build a C++ library for JDK 25" to "load a published native library".
- The fake adapter makes every AC testable without Telegram, including the ones that need a controlled clock.

**Negative**
- We depend on a third-party fork that may lag behind official TDLib releases. The facade keeps a later switch to option 2 inside one subproject.
- CLAUDE.md, the tech spec (rule 1, stack table) and foundation ADR-0002 name `org.drinkless.tdlib`. They need a wording update when `implement` adds the dependency (§11 follow-up).
- The native classifier must match the image's libc and OpenSSL. A base-image change can break loading, and the spike's check guards against that.

**Neutral**
- The `fake` adapter is production code in `telegram`, selected only by configuration. The README states that it is never for real use.

## Links

- Spec: [[../spec.md]] §8 OQ-2, §6
- SAD: [[../sad.md]] §2, §4, §7, §11
- Related ADR: foundation [[../../../adr/0002-single-app-with-isolated-tdlib-subproject]]
