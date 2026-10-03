---
status: Draft
owner: "Anton Husiev (Backend Lead)"
updated_at: "2026-10-03"
feature_size: M
---

# API sync report — app-shell

**Inputs read:** `data-model.md` ✓ (present, default path) · `sad.md` ✓ (`target_surfaces: [backend-service, web-frontend]` → OpenAPI. §6 has 3 seed flows plus Flows 4–8, and none is async, so there's no `events.md`) · `spec.md` ✓ (§4 US-43, US-70…US-74; §5 19 ACs) · ADR-0002…0006 · platform-skeleton `contracts/openapi.yaml` (conventions, shared components) · `telex/shared/Problems.kt`, `telex/web/ProblemHandler.kt`, `telex/web/api/MeController.kt`.
**Outputs:** `contracts/openapi.yaml` (OpenAPI 3.1, 6 operations: 4 new, 1 changed, 1 e2e-only; `spectral:oas` lint 0 findings at hint level and above). No `events.md`.
**Size / route:** M / standard (from `.size` / `.route`).

## A. Field origins

| schema_path | origin | confidence |
|---|---|---|
| getPulse.inboxCount | derived — sum of `InboxSource.countWaiting(ownerId)` (ADR-0003, sad §5 `inbox`); no column (data-model "Not stored") | medium |
| getPulse.conditions[] | derived — `StatusConditionSource.activeConditions(ownerId)` codes (ADR-0006, sad §5 `shared`); no column | medium |
| getMe.ownerId / email / linkedAccountCount | unchanged — platform-skeleton contract (owner.id, owner.email; linkedAccountCount low there) | high |
| getMe.theme | data-model.md → owner.theme VARCHAR(6), CHECK `owner_theme_ck` (3 values) | high |
| getMe.timeZone | data-model.md → owner.time_zone VARCHAR(64) NULL | high |
| getMe.timeZoneIsFallback | data-model.md → owner.time_zone_is_fallback BOOLEAN NOT NULL | high |
| changeMyPreferences.theme (req) | data-model.md → owner.theme | high |
| changeMyPreferences.timeZone (req) | data-model.md → owner.time_zone; never-null rule from AC-186 (enforced in `identity`) | high |
| changeMyPreferences.* (resp, Preferences) | data-model.md → owner.theme, time_zone, time_zone_is_fallback | high |
| saveDetectedTimeZone.timeZone (req) | data-model.md → owner.time_zone (64); device source per sad §8 Time and timezone | high |
| saveDetectedTimeZone.* (resp, Preferences) | data-model.md → the three owner columns | high |
| listTimeZones.items[] | derived — `ZoneId.getAvailableZoneIds()`, `Area/City` + `UTC` (sad §8 Known timezone list, `identity.TimeZones`); maxLength 64 = owner.time_zone | high |
| setPulseFixture.* | same shape as `Pulse` (sad §5 e2e fixture endpoint; in-memory, data-model "Not stored") | medium |

No field lacks an origin. The `medium` rows are computed values with no column, by design (ADR-0003, ADR-0006).

## B. Drift findings

1. **Endpoint ↔ data-model** *(core)* — ✓ `getMe`, `changeMyPreferences` and `saveDetectedTimeZone` read or write the three new `owner` columns. `listTimeZones` reads no table (runtime list), and `getPulse` reads only through the producer sources, whose tables arrive with E02/E11+. Every new data-model column is exposed by `getMe` and `Preferences`.
2. **Error code ↔ repo error definition** *(core)* — ✓ No new top-level `ErrorCode`: every refusal is `validation-failed` with a field code, as sad §8 decided. New **field** codes: `time-zone-required`, `unknown-time-zone` (named in sad §8) and `unknown-theme` (user decision 2026-10-03). **Required in `implement`:** `identity` raises them as `DomainProblem(400, "validation-failed", …, errors = [FieldProblem(field, code, …)])`. `PreferencesChange.theme` must bind as a string and be validated in code, because a Kotlin enum would fail in Jackson as an unreadable body with no `errors[]`. `MeBody` gains `theme`, `timeZone` and `timeZoneIsFallback`.
3. **Validation ↔ constraint** *(core)* — ✓ `Theme.enum` = `owner_theme_ck` (light, dark, system). `TimeZoneId.maxLength` 64 = `owner.time_zone VARCHAR(64)`. `Me.timeZone` is nullable, matching the nullable column. `Preferences.timeZone` is not nullable, because both writes always leave a zone saved. `timeZoneIsFallback: true` implies `UTC` (`owner_time_zone_fallback_ck`), which the examples respect. `Pulse.inboxCount` has `minimum: 0` and no maximum ("99+" is display only, AC-174).
4. **OpenAPI ↔ sequence** *(supporting)* — ✓ Every §6 `alt` branch has a response:
   - Seed flow 1: no live session → 401 `unauthenticated` / `session-ended`; live → 200 `Pulse`.
   - Seed flow 2: no answer, network error, proxy 502/503/504 → transport-level, documented in `info.description` and `getPulse` (not app responses); recovery → 200.
   - Seed flow 3: saved → 200; "no answer or refused" → 400 `unknown-theme` / 401 / 403 / no answer.
   - Flow 4: load account → `getMe` 200. Sign out is the unchanged platform-skeleton `signOut`.
   - Flow 5: refused not signed in / session ended → `NotSignedIn` on `getMe` and `getPulse`.
   - Flow 6: account theme → `getMe.theme`.
   - Flow 7: still unset → saved; another device saved first → 200 with the saved value (no error); unreadable or unknown → UTC with the fallback flag.
   - Flow 8: known list → `listTimeZones`; on the list → 200; empty → 400 `time-zone-required`; tampered → 400 `unknown-time-zone`; no answer → transport.

### Back-feed (coverage)

| AC | Operation / response | Note |
|---|---|---|
| AC-170, AC-43, AC-07b, AC-171 | — (client-only) | Navigation, layout and Coming soon are SPA routes; `getMe` 200 gates the shell |
| AC-172 | platform-skeleton `signOut` (unchanged) | Settings / Profile and security are SPA routes |
| AC-173 | `getMe` / `getPulse` → 401 `NotSignedIn` (both codes) | Destination memory is client-side (sad §8) |
| AC-174 | `getPulse` 200 `inboxCount`; `setPulseFixture` for e2e | 0 → no number, > 99 → "99+" in the SPA |
| AC-175 | `getPulse` — sources called with the session's Owner only | No parameter can name another Owner |
| AC-176, AC-177 | `getPulse` timeout / network error / proxy 5xx → banner | `Try again` = an immediate `getPulse` |
| AC-178 | `getPulse.conditions[]`; `setPulseFixture` for a second condition | Importance order and no-dismiss are in the SPA catalog |
| AC-179 | `changeMyPreferences` 200 (theme) | |
| AC-180 | — (client-only) | `prefers-color-scheme` |
| AC-181 | `getMe.theme` | One switch after `getMe` |
| AC-182 | `changeMyPreferences` 400 / 401 / 403 / no answer → revert | |
| AC-183 | `getMe.timeZone: null` → `saveDetectedTimeZone` 200 (saved / fallback) | |
| AC-184 | `listTimeZones` + `changeMyPreferences` 200 (timeZone) | Clears the fallback flag |
| AC-185 | `listTimeZones` (client-side search) | No server search |
| AC-186 | `changeMyPreferences` 400 `time-zone-required` | |

Every operation maps to a §4 user story: `getPulse` → US-71, US-72; `getMe` → US-70, US-73, US-74; `changeMyPreferences` → US-73, US-74; `saveDetectedTimeZone` → US-74; `listTimeZones` → US-74; `setPulseFixture` → US-71, US-72 (test-only). No sequence gap. The 403 CSRF responses are the inherited platform-skeleton convention, not a missing §6 branch.

## C. Deviations from the sdd defaults (inherited from platform-skeleton)

- Session cookie `telex_session` + `X-XSRF-TOKEN`, not `BearerAuth` (platform-skeleton ADR-0001).
- RFC 9457 `application/problem+json` with `code` / `errors[]`, not `{code, message, details?}`. Codes are kebab-case DNS-1123 labels, not `module.error_name`.
- Lists returned whole (`TimeZoneList`, about 400 ids), no cursor pagination.
- No `Idempotency-Key`. `PATCH` sets values, and `saveDetectedTimeZone` writes only while unset, so both are safe to repeat.
- Nullable via `oneOf [..., {type: "null"}]` (3.1 style), never `nullable: true`.

## D. Decisions taken in this stage (user-confirmed, 2026-10-03)

1. **`POST /api/v1/me/preferences/detected-time-zone`** is a separate operation for the first save (only if unset, never refuses, UTC fallback). sad §5's `MeController` line was patched to list it. §4, §8 and ADR-0005 already described the separate write.
2. **Field code `unknown-theme`** for a theme outside the enum.
3. **An empty `PATCH /me/preferences` body is a no-op** that returns the current preferences.
4. *(from sad §5, named here)* The e2e fixture endpoint is `PUT /api/v1/e2e-fixtures/pulse`. It exists only under the `e2e` profile, otherwise answers 404, and touches only the calling Owner.

## E. Open questions

None.
