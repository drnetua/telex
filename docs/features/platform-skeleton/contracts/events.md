---
status: Draft
owner: "Anton Husiev (Backend Lead)"
reviewers: ["Tech Lead"]
updated_at: "2026-10-02"
feature_size: M
---

# Events — platform-skeleton

Async contract for the flows drawn in `sad.md` §6 (Critical flow 2, Flow US-47). One event, internal to
the app: it is published and consumed inside `identity` through the Spring Modulith JDBC event
publication registry (ADR-0004). No external broker, no consumer outside the process. Derived from the
sequences — the event maps to the "record the session-started event" / "publish session-started"
messages in Critical flow 1, Flow US-01 sign in by code, Flow US-45 sign in with a passkey and Flow US-47.

## Channel: Modulith event publication registry (`event_publication` table)

- **Producer:** `identity` — `SignInSessions.start(...)`, in the same transaction that inserts the session.
- **Consumers:** `identity` notice listener (`internal/email`, `@ApplicationModuleListener`) → `mail.Mailer`.
- **Delivery:** at-least-once (after commit; a crash after sending may duplicate the email — sad §6 Flagged).
- **Ordering:** none.

## Event: `identity.sign-in-session-started.v1`

Kotlin type `telex.identity.SignInSessionStarted` (module root = public API). The registry stores it
serialised; the envelope fields below are the registry's columns, not part of the payload.

```json
{
  "event_id": "<event_publication.id — uuid>",
  "event_type": "telex.identity.SignInSessionStarted",
  "version": 1,
  "occurred_at": "<event_publication.publication_date — iso8601>",
  "data": {
    "ownerId": "<uuid — owner.id>",
    "sessionId": "<uuid — sign_in_session.id>",
    "createdAccount": "<boolean — true only for the account-creating redeem>"
  }
}
```

- **Required payload fields:** `ownerId`, `sessionId`, `createdAccount`. No email address, no secrets
  (sad §8 Events). The listener reads the address as created (`owner.email`) and the session's
  `user_agent_label`, `device_type`, `time_zone`, `started_at` itself.
- **Origin:** Critical flow 1 → "record SignInSessionStarted in the event registry"; Flow US-47 → "publish
  session-started with owner id, session id and created-account flag, no secrets".
- **Behaviour:** `createdAccount = true` → complete without an email (AC-98); otherwise send "New sign-in to
  teleX" with browser, device type, local time with the zone name, UTC, and the sessions link
  (`TELEX_PUBLIC_URL` + SCR-64 route, ADR-0006).
- **Backwards-compat policy:** additive-only. A new optional field is fine; removing or renaming one is a
  new type (`SignInSessionStartedV2`). Incomplete publications of the old type must still deserialise
  after an upgrade, so field removals wait until the registry holds none.

## Idempotency & retry

Numbers from the Flow US-47 retry note and dead-letter branch (ADR-0004, sad §6 Flagged "Retry shape").

- **Idempotency:** keyed by the publication id; a completed publication is never delivered again. A crash
  between sending and marking complete resends once — accepted duplicate (at-least-once).
- **Retry:** no backoff timer in E01. Incomplete publications are resubmitted on every app restart
  (`spring.modulith.events.republish-outstanding-events-on-restart=true`).
- **Dead-letter:** none separate — an incomplete row in `event_publication` *is* the dead letter, visible
  by SQL until a restart resends it (sad §11 risk "Notice email retried only on restart").

## Schema registry

- Registry: none — the Kotlin type in `telex.identity` is the schema; the registry stores Jackson JSON.
- Validator: the `@ApplicationModuleTest` scenario for Flow US-47 (`Scenario.publish(...)…andWaitForEventOfType`)
  pins the payload shape.
