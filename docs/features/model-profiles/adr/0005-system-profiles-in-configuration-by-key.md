---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: "S"
ticket: "E10 model-profiles"
---

# 0005 — Define system profiles in configuration and reference profiles by a key-or-id `ProfileRef`

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

Three system profiles (Fast and cheap, Balanced, Careful) ship with default models, and the Operator may override any slot in the installation settings (AC-225, AC-227). Custom profiles belong to one Owner. Every new Owner's default is Balanced (AC-51), and deleting the default custom profile returns it to Balanced (AC-220). E09 agents will store a reference to a profile, so the shape of that reference is seen by `agents`, `web` and every later epic.

## Decision drivers

- AC-225 / AC-227: Operator overrides per slot, validated at startup and on every catalog refresh, with a warning in the log.
- AC-222 / spec §6.1: every Owner-owned query filters on `owner_id`, with no shared rows to special-case.
- No Operator screen in E10 (E26 owns it), so the installation settings are the only place the Operator edits.

## Considered options

1. **Configuration, referenced by key.** The defaults live in `application.yaml` under `telex.models.system-profiles.<key>.<slot>`, and the Operator overrides them through environment settings. They are not stored in the database. A profile is referenced by `ProfileRef = System(key: fast | balanced | careful) | Custom(ModelProfileId)`. The Owner's default is stored only when it differs from Balanced.
2. **Database rows seeded by a migration.** System profiles are `model_profile` rows with `owner_id = NULL`, and their slots are re-synced from the settings at startup. There is one reference shape (`ModelProfileId`).

## Decision outcome

**Chosen:** option 1. The settings are the only source of truth for system profiles, so there is no startup sync that can drift. Profile tables hold only Owner-owned rows with `owner_id NOT NULL`, which keeps the AC-222 filter uniform. A new Owner needs no seeding: no stored default means Balanced.

## Consequences

**Positive**
- An Operator override takes effect on restart with nothing to migrate. Validation runs at startup and on each catalog refresh (AC-227).
- Removing a custom default (AC-220) is just deleting the Owner's default row.

**Negative**
- `ProfileRef` has two variants. It is stored as one text key (`system:balanced`, `custom:<uuid>`) or as two columns. `data-model` picks which, and the same encoding must be used by E09.
- System profile keys are a contract. Renaming one later means migrating every stored reference.

**Neutral**
- E26 (the Operator console) may later move system profiles into the database. `ProfileRef.System` would stay as the reference shape either way.

## Links

- Spec: [[../spec.md]] US-13, US-83, AC-51, AC-219, AC-220, AC-225, AC-227
- SAD: [[../sad.md]] §4, §8
- Related ADR: [[0002-profiles-in-agents-llm-thin-acl]]
