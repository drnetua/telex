# telegram-link — TDLight spike (T1)

Date: 2026-10-03 · Binding: `it.tdlight:tdlight-java` 3.5.5+td.1.8.66 (TDLib 1.8.66), natives `it.tdlight:tdlight-natives` 4.0.591 ·
Decision under test: [ADR-0004](adr/0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md).

Run unattended by the implementing agent on an Apple-silicon Mac (JDK 25 toolchain for Gradle). Anything needing a Telegram
account or a human is marked **pending**.

## Exit criteria

| # | Criterion (ADR-0004) | Outcome |
|---|---|---|
| 1a | Natives load on `eclipse-temurin:25-jre`, linux arm64 | **Pass.** Image Ubuntu 26.04, OpenJDK 25.0.4.1, `linux_arm64_gnu_ssl3` natives. `TdlightFacadeTest` green. |
| 1b | Natives load on `eclipse-temurin:25-jre`, linux x64 | **Pass** (run under `--platform linux/amd64` emulation on the Mac, 14 s instead of 2 s). `linux_amd64_gnu_ssl3` natives. A native x64 CI run is still worth watching on the first push. |
| 1c | Natives load on macOS | **Pass.** macOS arm64, `macos_arm64` natives, `./gradlew :backend:telegram-tdlib:test`. There is no macOS x64 native in TDLight; an Intel Mac developer cannot run the `tdlight` adapter (use `fake`). |
| 2 | Sign-in on Telegram's test servers completes | **Pending — manual, needs api_id/api_hash and a test account.** What is proven offline: the client reaches `authorizationStateWaitPhoneNumber` with `useTestDc=true`. |
| 3 | Two clients in one process | **Pass.** Two (and ten, in a throwaway probe) clients in one JVM, separate session directories, each reaches `authorizationStateWaitPhoneNumber` and reports `authorizationStateClosed` after `close()`. |
| 4 | Image grows by an acceptable amount | **Acceptable, with a cheap trim available.** See below. |

## Image size

No full image build was run; the numbers are from the artifacts that land in `app.jar` (Spring Boot nests the jars as they are).

| Artifact | Size |
|---|---|
| `tdlight-api` (TdApi) | 4.7 MB |
| `tdlight-java` | 0.2 MB |
| natives linux x64 / linux arm64 / macOS arm64 | 18.8 / 18.3 / 22.3 MB |
| Total added to `app.jar` (all three natives) | about 64 MB |
| Total if only the image's own platform is kept | about 24 MB |

At run time the loader unpacks one native (about 60 MB uncompressed in `/tmp`), so the container needs a writable temp dir.
Recommendation (not done here, for the Architect): exclude the macOS and the other-arch natives from `bootJar`.

## JDK 25 note

`System.loadLibrary` is called from an unnamed module, and JDK 25 prints a restricted-method warning that "will be blocked
in a future release". The `Dockerfile` ENTRYPOINT now passes `--enable-native-access=ALL-UNNAMED`. `bootRun` and tests
only warn.

## Memory per client

Measured in `eclipse-temurin:25-jre` (linux arm64, `-Xmx256m`), clients idle at `authorizationStateWaitPhoneNumber`, offline,
resident set size of the whole JVM:

| Clients | RSS |
|---|---|
| 0 (JVM + facade, natives not yet loaded) | 76 MB |
| 1 | 179 MB (first client pays about 100 MB: native library, TDLib runtime, JNI threads) |
| 10 | 273 MB (about 10 MB marginal per extra client) |

An idle, unauthenticated client is the floor. A connected account with a message/chat database and open connections will be
heavier, so the 30–60 MB budget (sad §7) is **not yet confirmed**: re-measure with signed-in accounts in the 50-account load run.

Observation: calling `System.exit(0)` immediately after `close()` aborted the JVM (`Aborted`) in the probe. Wait for
`authorizationStateClosed` before the JVM exits; T6's shutdown hook must do so.

## AC-117 termination timing

**Pending — manual, needs api_id/api_hash and a test account.** The idle-account timing of `authorizationStateClosed` after the
session is ended from another client is not measured. If it exceeds 5 minutes, raise a `/sdd:design` change for a periodic
liveness check (sad §6 flag).

## Unregistered number: when is it reported?

**Pending — manual, needs api_id/api_hash and a test account.** Input for T6: whether `PHONE_NUMBER_INVALID`/unregistered
comes back at the phone step or only after the code.

## Facade as landed

`telex.telegram.tdlib`: `TdlibFacade.open(TdlibClientConfig, TdlibUpdateListener): TdlibClient`; `TdlibClient.send(TdlibRequest)`
and `close()`. `TdlightFacade` is the only class importing `it.tdlight.*`; it answers `authorizationStateWaitTdlibParameters`
itself (session directory split into `db/` and `files/`, 32-byte key, test-DC flag) and sets TDLib log verbosity 1. Only
`GetAuthorizationState` is modelled; T6 adds requests.

## Result

All automatable exit criteria pass; no fallback to ADR-0004 option 2 is triggered. Criterion 2, the AC-117 timing and the
unregistered-number behaviour remain manual.
