---
id: T34
title: "Use contract error codes for passkey registration failures and validate the WebAuthn calls against openapi.yaml"
layer: "wiring"
deps: []
acs: ["AC-89", "AC-92", "AC-102", "AC-104", "AC-105"]
files_hint: ["backend/app/src/main/kotlin/telex/web/security/PasskeyCeremonies.kt", "backend/app/src/test/kotlin/telex/web/PasskeyRegistrationFilterTest.kt", "backend/app/src/integrationTest/kotlin/telex/web/PasskeyCeremoniesIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/SecurityChainIT.kt", "backend/app/src/integrationTest/kotlin/telex/web/ContractValidator.kt", "docs/features/platform-skeleton/contracts/openapi.yaml"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-02 re-review (R3, R4)"
status: "todo"
---

# T34 — Use contract error codes for passkey registration failures and validate the WebAuthn calls against openapi.yaml

Follow-up from the re-review. The findings, with `file:line` citations and suggested fixes, are rows R3, R4 in the "Re-review" section of [`_review/review-2026-10-02.md`](../_review/review-2026-10-02.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-89, AC-92, AC-102, AC-104, AC-105 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

A non-verification registration failure answers 500 `internal-error` (same as ProblemHandler), the unit test asserts it; PasskeyCeremoniesIT (and SecurityChainIT where it calls API endpoints) run ContractValidator.assertConforms on /webauthn/register/options, /webauthn/register, /webauthn/authenticate/options and /login/webauthn requests and responses; any contract gap this exposes is fixed in openapi.yaml or code.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt` for backend, `pnpm run check` in `frontend/` for UI (`e2e/` is type-checked by `./gradlew :e2e:check`).
- Commit with `SDD-Task: T34` and one `SDD-AC:` trailer per AC.
