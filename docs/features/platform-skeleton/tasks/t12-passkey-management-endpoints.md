---
id: T12
title: "List and remove my Passkeys, filtered by my WebAuthn user entity"
layer: "ports"
deps: ["T11"]
blocks: ["T20"]
acs: ["AC-89", "AC-91", "AC-92", "AC-97"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/Passkeys.kt", "backend/app/src/main/kotlin/telex/identity/internal/passkey/", "backend/app/src/main/kotlin/telex/web/api/PasskeysController.kt", "backend/app/src/integrationTest/kotlin/telex/web/"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T12 — List and remove my Passkeys, filtered by my WebAuthn user entity

## Place in the sequence

- **Blocked by:** T11 — Wire Spring Security WebAuthn for passkey registration and passkey sign-in into the one session mechanism · **Blocks:** T20 — Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator · **Wave:** 5 — needs the Spring passkey repositories and user-entity mapping (T11).
- **Lane:** shares `identity/internal/passkey/` with T11 (serialized after it) and `web/api/` with T9/T10.

## Why (user story)

> **As an** Owner
> **I want** to see my active Sign-in Sessions and Passkeys, end any session, and sign out
> **So that** a lost device or a stolen session stops working when I say so
>
> — `spec.md §4, US-46, verbatim` · full text: [spec.md](../spec.md)

This task shows the Owner their own passkeys and lets them remove any of them, the last one included.

## Inlined context

> **Authorization:** […] Passkeys are filtered by the Owner's WebAuthn user entity, because Spring's tables have no `owner_id` (ADR-0002). Another Owner's record is indistinguishable from a missing one, with the same `not-found` problem (AC-97).
>
> — `sad.md §8, Authorization, abridged` · full text: [sad.md](../sad.md)

> Flow US-46 manage passkeys: list my passkeys → read the credentials of this Owner's user entity only (AC-97) → *none* → empty list → No passkeys yet, with Add a passkey (AC-91) → *some* → passkeys with name, creation date and last-used date. Remove → delete the credential only if it belongs to this Owner's user entity → *not among this Owner's passkeys* → not found, exactly as for a passkey that never existed (AC-97) → *removed, even the last one* → open sessions stay, email sign-in keeps working (AC-92).
>
> — `sad.md §6, Flow US-46 manage passkeys on Profile and security, abridged` · full text: [sad.md](../sad.md)

> Decision deviation: any passkey can be removed, including the last one, while SCR-64 says the last passkey can't be deleted. The reason is that email sign-in always remains, so the rule adds no security, and it would keep a passkey on a lost device alive.
>
> — `spec.md §1, Traceability, verbatim` · full text: [spec.md](../spec.md)

Uses: T11's `UserCredentialRepository` / user-entity lookup by `name = OwnerId`. `Passkeys` is identity public API (sad §5: "list own, remove, label for a new credential").

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Column | Use | Change |
|---|---|---|
| `user_entities.name` | find the caller's user entity (`user_entities_name_uq`) | read |
| `user_credentials.credential_id`, `label`, `created`, `last_used`, `user_entity_user_id` (`user_credentials_user_entity_user_id_idx`) | list; hard delete `WHERE credential_id = :id AND user_entity_user_id = :mine` | read/delete |

— `data-model.md §Aggregate: Passkey, abridged` · full text: [data-model.md](../data-model.md)

## API contract

| operationId | Success | Errors |
|---|---|---|
| `listMyPasskeys` `GET /api/v1/passkeys` | `200 {items: [{id, label, createdAt, lastUsedAt}]}` newest first; `lastUsedAt: null` = "Never used"; `id` = credential id Base64URL | `401` |
| `removeMyPasskey` `DELETE /api/v1/passkeys/{passkeyId}` (`^[A-Za-z0-9_-]+$`, ≤1000) | `204` | `401`, `403`, `404 not-found` |

— `contracts/openapi.yaml, operationIds listMyPasskeys / removeMyPasskey, abridged` · full text: [openapi.yaml](../contracts/openapi.yaml)

## Acceptance criteria

### AC-89 — happy

> **Given** an Owner on a passkey-capable browser who has just signed in for the first time
> **When** they accept "Create a passkey" and confirm with their device
> **Then** the passkey is listed in Profile and security, named automatically after its browser and device (for example "Safari on iPhone"), with its creation date and its last-used date ("Never used" until first use), and their next sign-in on that device completes with "Sign in with a passkey" on the sign-in page and the device check alone, without typing an email address
>
> — `spec.md §5, AC-89, verbatim` · full text: [spec.md](../spec.md)

### AC-91 — happy

> **Given** an Owner on the passkey step after their first sign-in
> **When** they choose "Not now"
> **Then** they land on the Inbox, and Profile and security shows "No passkeys yet" with an "Add a passkey" action; the passkey step only follows the sign-in that creates the account and is not offered again on later sign-ins, on any device
>
> — `spec.md §5, AC-91, verbatim` · full text: [spec.md](../spec.md)

### AC-92 — happy

> **Given** an Owner with one or more Passkeys, including their only one
> **When** they remove a passkey in Profile and security and confirm
> **Then** the passkey disappears from the list and can no longer sign them in, and sign-in by email keeps working; Sign-in Sessions already open on any device stay open, and the confirm step reminds the Owner to end a lost device's session in the sessions list (AC-93)
>
> — `spec.md §5, AC-92, verbatim` · full text: [spec.md](../spec.md)

### AC-97 — authorization

> **Given** two Owners on the same installation
> **When** one Owner tries to see, end or remove the other Owner's Sign-in Session or Passkey
> **Then** nothing changes, and it looks as if that session or passkey doesn't exist; each Owner only ever sees their own
>
> — `spec.md §5, AC-97, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `identity/Passkeys.kt`: `listMine(ownerId)`, `removeMine(ownerId, credentialId): Boolean`.
- [ ] Owner-scoped delete in `identity/internal/passkey/` (never `deleteById` without the user-entity condition).
- [ ] `web/api/PasskeysController.kt`; `false` → `404 not-found`.
- [ ] `PasskeysApiIT`: two Owners; credentials saved via `UserCredentialRepository` with `label = "Test Browser on Test Device"` (data-model §Test fixtures).

## Edge cases

| Case | Behaviour |
|---|---|
| Owner with no user entity yet | `200 {items: []}` (AC-91) |
| Remove the only passkey | `204`; list empty; sessions still live (AC-92) |
| Owner A removes Owner B's credential id | `404 not-found`; B's passkey still listed (AC-97) |
| Malformed `passkeyId` | `400 validation-failed` |

## Definition of Done

- [ ] `PasskeysApiIT` covers every edge case and passes.
- [ ] A removed passkey then fails `/login/webauthn` with `401 passkey-rejected` (reuse T11's test helper).
- [ ] every Hard Rule inlined above still holds.
- [ ] `./gradlew spotlessCheck detekt` clean; `ModularityTest` green.
