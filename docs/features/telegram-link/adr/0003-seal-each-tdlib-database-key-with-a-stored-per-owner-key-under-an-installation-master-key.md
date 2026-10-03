---
status: Accepted
owner: "Anton Husiev (Architect)"
reviewers: ["Tech Lead", "Security Lead"]
updated_at: "2026-10-03"
feature_size: "M"
ticket: "E02 telegram-link"
---

# 0003 — Seal each TDLib database key with a stored per-Owner key under an installation master key

- **Status:** Accepted
- **Date:** 2026-10-03
- **Deciders:** Anton Husiev, Claude (design walk)

## Context

A Telegram session is a set of TDLib files (a SQLite database and a binlog) in one directory per session. TDLib encrypts them with a `database_encryption_key` it is given at start. The spec requires that 100% of stored session data is unreadable without the Owner's key (NFR-05). An unlinked account must leave 0 stored items that identify it, always, even when Telegram is unreachable (spec §6). The tech spec plans "envelope encryption (AES-GCM, key per Owner)" for TDLib keys and, later, BYOK secrets.

## Decision drivers

- Spec §6 "Session data at rest": 100% unreadable without the Owner's key, checked by a dump.
- Spec §6 "Unlink leaves nothing … always", including after a crash halfway through an unlink.
- Reuse by BYOK keys (E10/E11) without a second scheme.
- Module rules: `telegram` can't call `identity` (ADR-0002).

## Considered options

1. **Stored per-Owner key under a master key.** The installation master key (`TELEX_MASTER_KEY`, 256-bit, from config) wraps a random per-Owner key (AES-256-GCM), stored by `identity`. The per-Owner key wraps a random 32-byte TDLib database key per Telegram session, stored on the Linked Account by `messaging`.
2. **Per-Owner key derived with HKDF from the master key and the `OwnerId`** (HKDF: a function that turns one secret into many independent keys). Nothing is stored for the Owner. The rest is as in option 1.
3. **The whole TDLib directory as an encrypted blob in Postgres**, unpacked to a temporary disk at start.

## Decision outcome

**Chosen:** option 1. `identity` exposes `OwnerKeys.seal(ownerId, plaintext, aad)` / `open(ownerId, sealed, aad)` and never hands out the Owner key itself. The associated data (`aad`) is the `LinkedAccountId`, so a sealed key can't be moved to another account. `messaging` generates the TDLib key when a linking attempt starts and keeps it only in memory until the account is created. It then stores the key sealed, unseals it to start a client, and passes the raw bytes to the `telegram` port. The bytes live only in the memory of the running client. An unlink deletes the sealed key in the same transaction as the account row (ADR-0002). Without that key, any session directory left on disk is unreadable (crypto-shredding: destroying the key instead of relying on every copy of the data being deleted). A startup sweep then removes such orphan directories. Option 2 can't destroy or replace one Owner's key on its own, and rotating the master key would change every Owner's key at once. Option 3 fights TDLib's constant binlog writes and risks losing session state on a crash.

## Consequences

**Positive**
- NFR-05 holds for the TDLib files. The dump check looks for the raw key and finds it nowhere.
- "Leaves nothing" holds even when deleting files fails: the key is gone, so the files are noise.
- Rotating the master key re-wraps the Owner keys only, without touching TDLib.
- BYOK reuses `OwnerKeys` as-is.

**Negative**
- Losing `TELEX_MASTER_KEY` makes every session unreadable. Every Linked Account then goes to "Session lost", and Owners have to sign in again (§11).
- `TELEX_MASTER_KEY` becomes a required setting for linking. Without it, linking reports "isn't set up" exactly like missing Telegram credentials (AC-119).
- TDLib's `files` directory (downloaded media) is not covered by database encryption. E02 downloads no files, but E04 must revisit this (§11).

**Neutral**
- Each re-sign-in gets a new TDLib key with its new session, and the old one is deleted with the old directory.

## Links

- Spec: [[../spec.md]] §6 (session data at rest, unlink leaves nothing), §6.1, AC-111, AC-113
- SAD: [[../sad.md]] §4, §8
- Related ADR: [[0002-own-linked-accounts-and-their-chat-list-in-messaging-behind-a-session-only-telegram-acl]]
