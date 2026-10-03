---
id: T3
title: "Add OwnerKeys envelope encryption with the master-key check and reset, and SignInSessions.isLive"
layer: "app"
deps: ["T2"]
blocks: ["T8", "T12", "T17"]
acs: ["AC-110", "AC-119"]
files_hint: ["backend/app/src/main/kotlin/telex/identity/OwnerKeys.kt", "backend/app/src/main/kotlin/telex/identity/internal/key/", "backend/app/src/main/kotlin/telex/identity/SignInSessions.kt", "backend/app/src/integrationTest/kotlin/telex/identity/OwnerKeysIT.kt", "backend/app/src/test/kotlin/telex/identity/internal/key/"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T3 — Add OwnerKeys envelope encryption with the master-key check and reset, and SignInSessions.isLive

## Place in the sequence

- **Blocked by:** T2 — Promote the owner_key, linked_account and channel migrations into the live Flyway tree · **Blocks:** T8 — Start, resume, cancel and expire the in-memory linking attempt (one per Owner), T12 — Reconnect accounts on boot and follow Telegram's session state (Connected, Reconnecting, Session lost), T17 — Wire the Operator config, the session volume and the README Telegram setup step · **Wave:** 2 — needs the `owner_key` table (T2).
- **Lane:** own lane.

## Why (user story)

> **As an** Operator
> **I want** to give the installation its Telegram app credentials once, and to have Owners told plainly when linking isn't set up
> **So that** Owners can link accounts without me ever seeing their chats
>
> — `spec.md §4, US-53, verbatim` · full text: [spec.md](../spec.md)

This task gives the installation its encryption root (master key → Owner key → TDLib key) and the liveness check that ends a wizard when its Sign-in Session ends.

## Inlined context

> `identity` exposes `OwnerKeys.seal(ownerId, plaintext, aad)` / `open(ownerId, sealed, aad)` and never hands out the Owner key itself. The associated data (`aad`) is the `LinkedAccountId`, so a sealed key can't be moved to another account. […] Losing `TELEX_MASTER_KEY` makes every session unreadable. […] the app refuses to start while the configured key is missing or doesn't match it […]. Recovery from a truly lost key is an explicit Operator reset (`TELEX_MASTER_KEY_RESET=true`). It deletes every Owner key, sealed key and session directory, and every Linked Account goes to "Session lost" […]. On an installation that has never stored an Owner key, a missing key means linking reports "isn't set up", exactly like missing Telegram credentials (AC-119).
>
> — `adr/0003 §Decision outcome + §Consequences, abridged` · full text: [0003-seal-each-tdlib-database-key-with-a-stored-per-owner-key-under-an-installation-master-key.md](../adr/0003-seal-each-tdlib-database-key-with-a-stored-per-owner-key-under-an-installation-master-key.md)

> *Master-key check value* — not a separate table. Startup opens any one `owner_key` row: a wrong key fails the GCM tag and the app refuses to start; zero rows means an installation that has never stored an Owner key, where a missing key only makes linking report "isn't set up" (AC-119). `TELEX_MASTER_KEY_RESET=true` deletes every `owner_key` row, so the next key created starts the check afresh (user decision, 2026-10-03; deviation from the sad §7 / ADR-0003 wording "key-check value", same behaviour).
>
> — `data-model.md §Not stored, Master-key check value, verbatim` · full text: [data-model.md](../data-model.md)

> `identity` (core) gains `OwnerKeys` for envelope encryption and the master-key check (ADR-0003), plus a `SignInSessions.isLive(id)` query that the attempt sweep uses (AC-110).
> `TELEX_MASTER_KEY`: 32 random bytes, base64.
>
> — `sad.md §5 + §7 Operator config, abridged` · full text: [sad.md](../sad.md)

> **Hard rule:** Secrets: TDLib keys are generated with `SecureRandom` (32 bytes) and stored only sealed. The master key comes only from config. Never logged: […] TDLib keys.
>
> — `sad.md §8, Secrets + Logging, abridged` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

| Column | Type | Constraints | Change |
|---|---|---|---|
| `owner_key.owner_id` | UUID | PK, FK → `owner(id)` | written (lazy create), read |
| `owner_key.sealed_key` | BYTEA | NOT NULL, `octet_length = 60` (12-byte nonce ‖ 32-byte ct ‖ 16-byte tag), AAD = owner id | written, read |
| `owner_key.created_at` | TIMESTAMPTZ | NOT NULL, from `Clock` | written |
| `sign_in_session` | — | existing liveness rules (E01) | read-only (`isLive`) |

Access: open the Owner's key → PK; startup check → any one row (`LIMIT 1`); reset → delete every row.

— `data-model.md §Aggregate: Owner key, abridged` · full text: [data-model.md](../data-model.md)

## API contract

Internal — no API surface. (Public module API: `OwnerKeys.seal/open`, `OwnerKeys.ready()` — false when no master key is configured on an installation with no Owner key — `OwnerKeys.resetPerformedAtStartup`, `SignInSessions.isLive(id)`.)

## Acceptance criteria

### AC-110 — cross-context

> **Given** an Owner with a connected Linked Account
> **When** they sign out of teleX, or their Sign-in Session ends or is revoked
> **Then** the Linked Account stays connected and keeps syncing, and the next time they sign in it is still there; and an Owner whose Sign-in Session ends in the middle of the linking wizard must sign in again and start the wizard over, with no session kept from the unfinished attempt
>
> — `spec.md §5, AC-110, verbatim` · full text: [spec.md](../spec.md)

### AC-119 — error

> **Given** an installation whose Operator hasn't given it Telegram app credentials
> **When** an Owner chooses "Connect Telegram" or "Add account"
> **Then** teleX says that Telegram linking isn't set up on this installation yet and that the Operator has to finish the setup, and doesn't start the wizard
>
> — `spec.md §5, AC-119, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] Bind `telex.master-key` (`TELEX_MASTER_KEY`, base64 of 32 bytes) and `telex.master-key-reset` (`TELEX_MASTER_KEY_RESET`) in `identity/internal/key/` config properties
- [ ] `OwnerKeys` (public, `identity/OwnerKeys.kt`): `seal(ownerId, plaintext, aad)` / `open(ownerId, sealed, aad)`, AES-256-GCM with a random 12-byte nonce; lazily create + store the Owner key sealed under the master key (AAD = owner id) in `internal/key/` rows via `JdbcClient`
- [ ] Startup check (`ApplicationRunner` ordered before messaging's boot work): reset flag set → delete every `owner_key` row and remember `resetPerformedAtStartup = true`; else if any row exists → open it, and fail startup with an error naming `TELEX_MASTER_KEY` when the key is missing or the tag fails
- [ ] `OwnerKeys.ready()` for the AC-119 "not set up" check
- [ ] `SignInSessions.isLive(id)` (`identity/SignInSessions.kt`) reusing E01's live predicate, without bumping activity
- [ ] Unit tests for seal/open (wrong AAD fails, tamper fails); `OwnerKeysIT` for lazy create, startup refusal (missing / wrong key), fresh-install not-ready, reset, `isLive` on live/ended/revoked sessions

## Edge cases

| Case | Behaviour |
|---|---|
| Master key missing, no `owner_key` rows | App starts; `ready()` = false → linking answers `telegram-linking-not-set-up` (AC-119) |
| Master key missing or wrong, ≥1 `owner_key` row | App refuses to start with an error naming the setting; no account shown in a false state |
| `TELEX_MASTER_KEY_RESET=true` with the new key | Every `owner_key` row deleted; `resetPerformedAtStartup` = true for messaging (T12) to send accounts to Session lost |
| `open` with another account's AAD | GCM failure — a sealed key can't move between accounts |

## Definition of Done

- [ ] unit + `OwnerKeysIT` tests above pass
- [ ] no key material in logs (asserted by T24's log scan; no `toString` of key bytes)
- [ ] detekt + ktlint clean; `ModularityTest` green
- [ ] every Hard Rule inlined above still holds
