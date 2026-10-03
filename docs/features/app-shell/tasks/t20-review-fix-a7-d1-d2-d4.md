---
id: T20
title: "Make the preferences and pulse endpoints honour the contract: nullable timeZone, refused unknown keys, applied condition pattern, one-query /me"
layer: "ports"
deps: []
acs: ["AC-183", "AC-178"]
files_hint: ["backend/app/src/main/kotlin/telex/web/api/MeController.kt", "backend/app/src/main/kotlin/telex/web/api/PulseController.kt", "backend/app/src/main/kotlin/telex/identity/OwnerProfiles.kt", "backend/app/src/main/kotlin/telex/identity/OwnerPreferences.kt", "backend/app/src/main/kotlin/telex/identity/internal/owner/Owners.kt", "backend/app/src/integrationTest/kotlin/telex/web/", "backend/app/src/integrationTest/kotlin/telex/identity/", "docs/features/app-shell/contracts/openapi.yaml"]
owner: "Anton Husiev"
estimate: "S"
source: "review-2026-10-03 (A7, D1, D2, D4)"
status: "todo"
---

# T20 — Make the preferences and pulse endpoints honour the contract: nullable timeZone, refused unknown keys, applied condition pattern, one-query /me

Follow-up from the independent review. The findings, with `file:line` citations and suggested fixes, are rows A7, D1, D2, D4 in [`_review/review-2026-10-03.md`](../_review/review-2026-10-03.md) — read those rows first; they are this task's brief.

## Acceptance criteria touched

AC-183, AC-178 — verbatim text in [spec.md §5](../spec.md); screen states in [screens.md](../screens.md); contract in [contracts/openapi.yaml](../contracts/openapi.yaml).

## Definition of done

PreferencesApiIT: PATCH {theme} for an Owner with no saved zone answers timeZone null + timeZoneIsFallback false, matching the next GET /me, and passes ContractValidator against the updated openapi.yaml (Preferences.timeZone nullable); PATCH with an unknown key (e.g. {"themes":"dark"}) answers 400 validation-failed and changes nothing; PulseFixtureApiIT: PUT /api/v1/e2e-fixtures/pulse with a malformed condition code answers 400 validation-failed; GET /me reads email and preferences in one SELECT (OwnerProfiles/Owners), all existing ITs stay green.

- RED first (failing test quoted), then GREEN, then the per-task gate: `./gradlew test integrationTest spotlessCheck detekt`.
- Commit with `SDD-Task: T20` and one `SDD-AC:` trailer per AC.
