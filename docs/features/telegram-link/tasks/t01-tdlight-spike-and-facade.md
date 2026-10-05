---
id: T1
title: "Prove TDLight on JDK 25 and land the binding behind the TdlibFacade (spike)"
layer: "infra"
deps: []
blocks: ["T6"]
acs: []
files_hint: ["gradle/libs.versions.toml", "backend/telegram-tdlib/", "Dockerfile", "CLAUDE.md", "docs/docs/01-tech-spec.md", "docs/features/telegram-link/spike.md"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "done"
---

# T1 — Prove TDLight on JDK 25 and land the binding behind the TdlibFacade (spike)

## Place in the sequence

- **Blocked by:** — · **Blocks:** T6 — Implement the tdlight adapter: TDLib auth states, errors and chat list mapped to the port · **Wave:** 1 — first E02 task by decision (spec §8 OQ-2 default, ADR-0004) — runs before any wizard work, in parallel with the migrations and the fake adapter.
- **Lane:** shares `backend/telegram-tdlib/` with T6; shares `docs/docs/01-tech-spec.md` with T17 — serialized.

## Why (user story)

> **As an** Owner
> **I want** my Linked Accounts to come back on their own after teleX restarts
> **So that** I never have to type a Telegram code again just because the server restarted
>
> — `spec.md §4, US-52, verbatim` · full text: [spec.md](../spec.md)

This task retires the project's riskiest unknown (roadmap D1): without a TDLib binding that runs on JDK 25 in the image, no Linked Account can exist or reconnect.

## Inlined context

> **Chosen:** option 1 [TDLight Java]. There is no C++ toolchain in the build, and the same artifact works in CI, in the image and on the developer's Mac. The binding stays an `implementation` dependency of `backend/telegram-tdlib`. Only `telex.telegram.tdlib` code imports `it.tdlight.*`, and the rest of `telegram` sees our own facade types. Rule 1 keeps its meaning, but its package name changes.
> […] If the spike fails, option 2 [official TDLib built in a Dockerfile stage] is the fallback, and only `backend/telegram-tdlib` and the Dockerfile change.
>
> — `adr/0004 §Decision outcome, abridged` · full text: [0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md](../adr/0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md)

> **AC-117's 5-minute bound** (flow 9) assumes TDLib reports a closed authorization on its own soon after the session is ended in Telegram. The TDLib spike should confirm this on an idle account. If it doesn't hold, a periodic liveness check per Telegram session is needed, which is a design change to §8.
>
> — `sad.md §6, Coverage — Flagged for design, verbatim` · full text: [sad.md](../sad.md)

> Target ≥ 50 connected Linked Accounts on 4 vCPU / 8 GB (spec §6, tech-spec NFR-04). Budget: about 30–60 MB of native memory per TDLib client, measured in the spike and in the 50-account load run, so the JVM heap is capped to leave room.
>
> — `sad.md §7, Scaling thresholds, abridged` · full text: [sad.md](../sad.md)

> **Docs drift.** […] CLAUDE.md, the tech spec and foundation ADR-0002 name `org.drinkless.tdlib`. […] `implement` updates […] rule 1's package name and CLAUDE.md when it lands the dependency.
>
> — `sad.md §11, Docs drift, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** No module other than `telegram` imports `org.drinkless.tdlib.*` — after this task: no code outside `telex.telegram.tdlib` imports `it.tdlight.*`; the binding is an `implementation` dependency of `backend/telegram-tdlib` only.
>
> — `CLAUDE.md §Target architecture, rule 1 + adr/0004, abridged` · full text: [0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md](../adr/0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — no API surface.

## Acceptance criteria

No spec §5 AC is satisfied by the spike alone; it de-risks every one of them. Its acceptance is the spike's exit list from ADR-0004:

> The first E02 task is a spike that has to show all of this before any wizard work:
> - the natives load on `eclipse-temurin:25-jre` (linux x64) and on macOS;
> - a sign-in on Telegram's test servers completes;
> - two clients run in one process;
> - the image grows by an acceptable amount.
>
> — `adr/0004 §Decision outcome, spike exit criteria, verbatim` · full text: [0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md](../adr/0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md)

## Checklist

- [ ] Add TDLight Java (pin one version) + its native classifiers for linux x64/arm64 and macOS to `gradle/libs.versions.toml`; wire it as `implementation` in `backend/telegram-tdlib/build.gradle.kts` (replace the `org.drinkless` comment)
- [ ] Grow `TdlibFacade` (`backend/telegram-tdlib/src/main/kotlin/telex/telegram/tdlib/`) to the minimum the adapter needs: create a client for a session directory + 32-byte database key + `api_id`/`api_hash` + test-DC flag, send a function, receive updates on a callback, close; facade types only, no `it.tdlight.*` in its signatures; TDLib log verbosity 1
- [ ] Add a facade test that loads the natives and starts + closes two clients in one JVM against temporary directories (no network); run it on macOS and inside the `eclipse-temurin:25-jre` image (`Dockerfile` / a throwaway script)
- [ ] Manual: sign in on Telegram's test servers through the facade; end the session from another client on an idle account and time how long TDLib takes to report `authorizationStateClosed` (the AC-117 5-minute assumption)
- [ ] Write `docs/features/telegram-link/spike.md`: outcome per exit criterion, image size before/after, resident memory per client, the termination timing, and whether an unregistered number is reported at the phone step or only after the code (input for T6)
- [ ] Rename rule 1's package in `CLAUDE.md` and `docs/docs/01-tech-spec.md` (rule 1 + stack table) from `org.drinkless.tdlib` to the TDLight package
- [ ] If any exit criterion fails: stop, record it in `spike.md`, and switch to ADR-0004 option 2 (official TDLib in a Dockerfile stage) — only `backend/telegram-tdlib` and the `Dockerfile` change

## Edge cases

| Case | Behaviour |
|---|---|
| Natives fail to load in `eclipse-temurin:25-jre` (libc/OpenSSL mismatch) | Spike fails → fallback option 2 of ADR-0004; nothing above the port changes |
| Idle session not reported closed within 5 min | Record it in `spike.md` and raise a `/sdd:design` change for a periodic liveness check (sad §6 flag) — do not invent one here |
| Image growth unacceptable | Record the numbers; decide with the Architect before T6 |

## Definition of Done

- [ ] facade test (natives load, two clients in one process) passes on macOS and in the runtime image
- [ ] `spike.md` records all four exit criteria, memory per client and the termination timing
- [ ] `./gradlew :backend:telegram-tdlib:build` and `ModularityTest` green; no `it.tdlight.*` import outside `telex.telegram.tdlib`
- [ ] every Hard Rule inlined above still holds
