---
status: Draft
owner: "Anton Husiev (Backend Lead)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-03"
feature_size: S
---

# Events — model-profiles

Async contract for the events in `sad.md` §6 and §8 "Events". All three are internal to the app: they are
published through the Spring Modulith JDBC event publication registry (baseline `event_publication`
table) and consumed with `@ApplicationModuleListener`. There is no external broker. Event types live at
the producing module's root (public API).

## Channel: Modulith event publication registry (`event_publication` table)

- **Delivery:** at-least-once, after the publishing transaction commits.
- **Ordering:** none.
- **Payloads:** ids, keys and enum values only. Never request or answer text, profile names or the provider key (spec §6.1, sad §8 Logging).

## Event: `llm.model-catalog-refreshed.v1`

Kotlin type `telex.llm.ModelCatalogRefreshed`.

```json
{
  "event_id": "<event_publication.id — uuid>",
  "event_type": "telex.llm.ModelCatalogRefreshed",
  "version": 1,
  "occurred_at": "<event_publication.publication_date — iso8601>",
  "data": {
    "refreshedAt": "<iso8601 — model_catalog_state.last_refreshed_at>",
    "modelCount": "<integer — rows in model_catalog_entry after the replace>"
  }
}
```

- **Producer:** `llm` catalog refresher, in the transaction that replaces the snapshot (Critical flow 2, "publishes the catalog refreshed event").
- **Consumers:** `agents` system-profile validator. It re-validates the Operator's slot overrides against the new catalog and logs one WARN per bad model, naming the profile, slot and model (AC-227).
- **Origin:** sad §6 Critical flow 2 → "Llm->>Agents: publishes the catalog refreshed event".
- **Not published** on a failed refresh or when no key is configured. The startup validation runs on application start against the loaded snapshot, so a restart without a successful refresh still warns.
- **Idempotency:** the listener is a pure re-validation (log only), so a redelivery only repeats the warnings.

## Event: `agents.model-profile-deleted.v1`

Kotlin type `telex.agents.ModelProfileDeleted`.

```json
{
  "event_id": "<uuid>",
  "event_type": "telex.agents.ModelProfileDeleted",
  "version": 1,
  "occurred_at": "<iso8601>",
  "data": {
    "ownerId": "<uuid — model_profile.owner_id>",
    "profileId": "<uuid — model_profile.id>",
    "wasDefault": "<boolean — a default_model_profile row pointed to it>"
  }
}
```

- **Producer:** `agents` `ModelProfiles.delete`, in the delete transaction (Critical flow 6).
- **Consumers:** none in E10. E09 will move agents that used the profile to the Owner's default, with a card warning (spec §8 open question 1, feature ADR-0001).
- **Origin:** sad §6 Critical flow 6 → "publishes the profile deleted event for E09".
- **Idempotency:** consumers dedupe on `profileId` (a deleted profile can't be deleted twice).

## Event: `agents.model-call-finished.v1`

Kotlin type `telex.agents.ModelCallFinished`.

```json
{
  "event_id": "<uuid>",
  "event_type": "telex.agents.ModelCallFinished",
  "version": 1,
  "occurred_at": "<iso8601 — model_call.finished_at>",
  "data": {
    "callId": "<uuid — model_call.id>",
    "ownerId": "<uuid — model_call.owner_id>",
    "profile": { "kind": "system", "key": "balanced" },
    "slot": "<text | vision | image — model_call.slot>",
    "outcome": "<answered | no-model-available | no-model-answered | ai-not-configured — model_call.outcome>",
    "answeredByModelId": "<string | null — model_call.answered_by_model_id>",
    "fallback": "<boolean — model_call.fallback>"
  }
}
```

- **Producer:** `agents` `ProfileCalls`, right after the `model_call` row is written (feature ADR-0004). If writing the record fails, the call still returns its answer and no event is published (sad §8 "Call record write").
- **Consumers:** none in E10. Intended for `audit` and E27 budgets. Attempts aren't in the payload, so a consumer that needs them reads them through the `agents` API by `callId`.
- **Origin:** sad §8 Events, feature ADR-0004 (the call path in Critical flow 1, "appends a content-free call record").
- **Idempotency:** consumers dedupe on `callId`.

## Backwards-compat policy (all three)

Additive only. A new optional field is fine. Removing or renaming a field means a new type (`…V2`), because
incomplete publications of the old type must still deserialise after an upgrade.

## Retry & dead-letter

- **Retry:** no backoff timer. Incomplete publications are resubmitted on every app restart (`spring.modulith.events.republish-outstanding-events-on-restart=true`, as in platform-skeleton).
- **Dead-letter:** none separate. An incomplete row in `event_publication` is the dead letter, visible by SQL until a restart resends it.

## Schema registry

- Registry: none. The Kotlin types at the module roots are the schema, and the registry stores Jackson JSON.
- Validator: `@ApplicationModuleTest` scenarios (`Scenario.stimulate(...).andWaitForEventOfType(...)`) pin each payload.
