---
id: T10
title: "Persist custom profiles, their chains and the default profile, always scoped by Owner"
layer: "infra"
deps: ["T1", "T7"]
blocks: ["T11"]
acs: ["AC-218", "AC-220", "AC-222"]
files_hint: ["backend/app/src/main/kotlin/telex/agents/internal/profile/ProfileRepository.kt", "backend/app/src/main/kotlin/telex/agents/internal/profile/DefaultProfileRepository.kt", "backend/app/src/integrationTest/kotlin/telex/agents/profile/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T10 — Persist custom profiles, their chains and the default profile, always scoped by Owner

## Place in the sequence

- **Blocked by:** T1 — Promote the four staged model-profiles migrations into the live Flyway tree, T7 — Model the custom profile aggregate, ProfileRef and the profile rules in plain Kotlin · **Blocks:** T11 — Serve the catalog view, the profile list with picker data, one profile and a new-profile draft · **Wave:** 3 — needs the tables (T1) and the aggregate (T7).
- **Lane:** own lane (`internal/profile/*Repository.kt`).

## Why (user story)

> **As an** Owner
> **I want** to create, duplicate, edit and delete my own Model Profiles, with a Fallback Chain in each slot
> **So that** my helpers use the models I trust, with backups I chose
>
> — `spec.md §4, US-81, verbatim` · full text: [spec.md](../spec.md)

This task stores an Owner's profiles so that only that Owner can ever reach them and the default can never point at a deleted profile.

## Inlined context

> Custom profiles and default profiles are looked up only by `(owner_id, id)`. Another Owner's profile is `404 profile-not-found`, the same as a missing one (AC-222). System profiles are read-only for everyone (`409 system-profile-read-only`, AC-219)
>
> — `sad.md §8, Authorization / tenancy, verbatim` · full text: [sad.md](../sad.md)

**Note:** the contract finalized the not-found code as plain `not-found` (`openapi.yaml` header: "`not-found` reused for AC-222") — use `not-found`, not `profile-not-found`.

> **Access patterns:**
> - find my profile by id (edit, delete, set as default, resolve for a call; AC-222) → `model_profile_owner_id_id_uq`;
> - list my profiles, and count them for the 20 limit (AC-218) → leading `owner_id` […];
> - name taken? (AC-214, the "Balanced copy N" pick in AC-213) → `model_profile_owner_id_lower_name_uq`.
> **Limit of 20 (AC-218) under concurrency:** the save transaction first takes `pg_advisory_xact_lock(<hash of owner_id>)`, then counts the Owner's profiles and inserts. Two parallel creates by one Owner are serialized, so the 21st fails as `profile-limit-reached`.
>
> — `data-model.md §Entities, model_profile, abridged` · full text: [data-model.md](../data-model.md)

> A save deletes the profile's rows and inserts the new chains in the same transaction as the profile row, so a reorder (AC-213) never hits the unique keys halfway. Positions are written contiguously from 1 by code. An empty vision or image slot has no rows ("Not used", AC-223).
>
> — `data-model.md §Entities, model_profile_slot_model, abridged` · full text: [data-model.md](../data-model.md)

> **Delete of the default custom profile (AC-220):** the delete transaction first runs `DELETE FROM default_model_profile WHERE owner_id = ? AND custom_profile_id = ?`. Its row count tells the service whether to say "Balanced is now the default". Then it deletes the profile. The FK's `ON DELETE CASCADE` is the backstop. Choosing Balanced deletes the row, so "stored only when it differs from Balanced" holds (ADR-0005).
>
> — `data-model.md §Entities, default_model_profile, abridged` · full text: [data-model.md](../data-model.md)

> Every Owner-owned row carries `owner_id` and every query filters on it (precedent: `identity/internal/owner/Owners.kt`).
>
> — `sad.md §2, Conventions, verbatim` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [public-api.md](../contracts/public-api.md) ·
[events.md](../contracts/events.md) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Table | Columns | Change |
|---|---|---|
| `model_profile` | `id`, `owner_id`, `name` (trimmed), `created_at` — insert, update name, delete, select by `(owner_id, id)` / by `owner_id` ordered by `created_at`, count by `owner_id`, names by `owner_id` | read / write |
| `model_profile_slot_model` | `model_profile_id`, `slot`, `position` (1…3 contiguous), `model_id` — delete-all + insert per save; load with `model_profile_id = ANY(?)` | read / write |
| `default_model_profile` | `owner_id`, `system_profile_key` \| `custom_profile_id`, `chosen_at` — upsert on PK, delete for Balanced and on profile delete | read / write |

— `data-model.md §Entities + §Indexes, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no API surface.

## Acceptance criteria

### AC-218 — domain invariant (US-81)

> **Given** an Owner who already has 20 custom profiles
> **When** the Owner tries to create or duplicate another one
> **Then** nothing is created and the Owner is told that the limit is 20 custom profiles and that deleting one makes room
>
> — `spec.md §5, AC-218, verbatim` · full text: [spec.md](../spec.md)

### AC-220 — happy (US-81)

> **Given** an Owner whose default profile is their custom profile "Cheap vision"
> **When** the Owner deletes "Cheap vision" and confirms
> **Then** the profile disappears from their list, the default goes back to Balanced, and the Owner is told that Balanced is now the default
>
> — `spec.md §5, AC-220, verbatim` · full text: [spec.md](../spec.md)

### AC-222 — authorization (US-81)

> **Given** two Owners, where the first has a custom profile "Night shift"
> **When** the second Owner browses their profiles, opens a link to "Night shift" or tries to make it their default
> **Then** the second Owner never sees "Night shift", and the link shows the same "not found" page as a profile that doesn't exist, so the profile's existence isn't revealed
>
> — `spec.md §5, AC-222, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `ProfileRepository` (JdbcClient): `find(ownerId, id)`, `list(ownerId)` with chains in one extra query, `count(ownerId)`, `names(ownerId)`, `insert`/`update` with chain replace, `delete(ownerId, id)`, `lockOwner(ownerId)` (`pg_advisory_xact_lock`) — `backend/app/src/main/kotlin/telex/agents/internal/profile/ProfileRepository.kt`
- [ ] `DefaultProfileRepository`: `get(ownerId) → ProfileRef?` (null = Balanced), `set(ownerId, ref, at)` (Balanced deletes the row), `clearIfCustom(ownerId, id) → Boolean` — `backend/app/src/main/kotlin/telex/agents/internal/profile/DefaultProfileRepository.kt`
- [ ] Integration tests with two Owners, a parallel-create race and the delete-default flow — `backend/app/src/integrationTest/kotlin/telex/agents/profile/`

## Edge cases

| Case | Behaviour |
|---|---|
| Owner B asks for Owner A's profile id | Empty result, exactly as for a random id |
| Two parallel creates when the Owner has 19 | One succeeds, the other sees 20 under the lock and is refused |
| Parallel rename to the same name | Unique index rejects one; surfaced as `name-taken` |
| Delete a profile that isn't the default | Profile and chains gone, `clearIfCustom` false |
| Set default to Balanced | Row deleted; `get` returns null = Balanced |

## Definition of Done

- [ ] the repository integration tests pass on Testcontainers
- [ ] no query in these files omits `owner_id`
- [ ] every Hard Rule inlined above still holds
- [ ] detekt + ktlint 0 warnings; `ModularityTest` (`ApplicationModules.verify()`) green
