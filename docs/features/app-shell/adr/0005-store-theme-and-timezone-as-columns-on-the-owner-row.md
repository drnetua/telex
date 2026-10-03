---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E06 app-shell-responsive"
---

# 0005 — Store theme and timezone as columns on the `owner` row

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

The Owner's theme (light, dark or system) and timezone are saved on their account and follow them to every device (AC-179…AC-186). Other modules will read the timezone: quiet hours (E19) and the morning digest (E20). The `owner` table in `identity` today holds only `id`, `email`, `canonical_email` and `created_at`.

## Decision drivers

- AC-186: "an Owner always has exactly one timezone", and only zones from the known list are accepted (spec §6.1 abuse case).
- AC-183: before the first open after this change, an Owner has no timezone yet.
- Other modules read the timezone through `identity`'s public API, never its table.

## Considered options

1. **Typed columns on `owner`.** `theme VARCHAR(6) NOT NULL DEFAULT 'system'` with a check on light, dark or system, `time_zone VARCHAR(64) NULL`, where null means not saved yet, and `time_zone_is_fallback BOOLEAN NOT NULL DEFAULT false`, set when the first save had to fall back to UTC (AC-183's hint) and cleared when the Owner picks a zone.
2. **A key-value `owner_preference(owner_id, key, value)` table,** with theme and timezone as two rows.

## Decision outcome

**Chosen:** option 1. The database enforces the theme values and the column types, reads come with the Owner in one query, and `me` needs no join. The key-value table makes adding a setting cheaper, but it leaves every invariant to code and gives other modules a string key to read instead of a typed API.

## Consequences

**Positive**
- `GET /api/v1/me` returns `theme`, `timeZone` and `timeZoneIsFallback` with no extra query. The "pick your own" hint shows exactly when AC-183 asks, on every device. `PATCH /api/v1/me/preferences` changes either field.
- `identity` publishes a typed read, `OwnerPreferences.timeZoneOf(ownerId)`, for E19 and E20.
- The timezone is checked against the server's list (`GET /api/v1/time-zones`, built from `java.time.ZoneId` and limited to `Area/City` names plus `UTC`). Setting it empty is refused.

**Negative**
- Each future setting is a new column and a migration (with its rollback).
- `time_zone` is nullable until first set, so "exactly one timezone" holds only after the first save. The first save is a separate "set if not yet saved" write, so racing tabs and a later device in another zone don't overwrite it (AC-183).

**Neutral**
- Moving to a preferences table later is a small migration that copies two columns.

## Links

- Spec: [[../spec.md]] AC-179…AC-186, §6.1
- SAD: [[../sad.md]] §4, §5, §8
- Related ADR: platform-skeleton [[../../platform-skeleton/adr/0001-keep-sign-in-sessions-in-an-identity-owned-table-behind-an-opaque-cookie]]
