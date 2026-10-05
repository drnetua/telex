## Summary

This PR ships E02: an Owner links their own Telegram accounts to teleX, watches them stay connected and sync their chat list, and can fully unlink them.

- **Linking wizard** (SCR-02): phone → Sign-in Code → two-step password, with every Telegram refusal and wait explained in plain language. The wait shows a countdown to a time in the Owner's zone.
- **Linked Accounts** (SCR-10 line, SCR-60 Accounts): name, masked phone, Connected / Reconnecting / Session lost, chat-sync progress, and the chat count, kept current within a minute.
- **Resilience:**
  - accounts reconnect by themselves after a restart;
  - an outage shows Reconnecting, never Session lost;
  - a session ended in Telegram shows Session lost and "Sign in again" in the shell's Status Banner;
  - a chat-list load that fails is retried after Telegram's retry-after or a backoff of 5 s up to 300 s.
- **Unlink:** signs teleX out of Telegram and deletes the session and every synced chat entry. If Telegram can't confirm, the Owner is told to check active sessions.
- **Security:** envelope encryption seals each TDLib key with a per-Owner key, which a master key wraps. TDLib stays inside the `telegram` module. Operators never see Owner data.

The branch is rebased on `master` (E10 model-profiles), and the telegram-link migrations are renumbered after model-profiles'.

- Spec: [`docs/features/telegram-link/spec.md`](docs/features/telegram-link/spec.md)
- Changelog: [`docs/features/telegram-link/_ship/changelog.md`](docs/features/telegram-link/_ship/changelog.md)

## Acceptance criteria

- AC-01: link an account, with or without two-step verification. It shows Connected with its name and masked phone, and syncing starts ✓
- AC-02: wrong or expired code → retry or a new code; Telegram's wait ends the attempt with a countdown, and starting again shows the remaining wait ✓
- AC-106: wrong password → the hint and the reset note, and it can be retried ✓
- AC-107: invalid, unregistered or banned number → blocked, with the reason; teleX never creates a Telegram account ✓
- AC-04: one Telegram account belongs to one Owner; a second Owner's sign-in is ended ✓
- AC-108: re-linking the same account creates no duplicate ✓
- AC-109: an abandoned attempt is discarded with no device left; one open attempt per Owner, which survives reload and a second tab ✓
- AC-110: the account stays linked across the Owner's sign-out; a mid-wizard session end starts over ✓
- AC-03: other Owners and the Operator never see the account ✓ (Operator half N/A by design)
- AC-111: unlink signs out, deletes the session and chat entries, and returns to Connect Telegram after the last one ✓
- AC-112: an unlinked account is gone everywhere; re-linking starts fresh ✓
- AC-113: an unconfirmed sign-out still deletes everything, and tells the Owner to check active sessions ✓
- AC-114: several accounts, each with its own state and sync ✓
- AC-115: the per-Owner account limit (default 3), with nothing left in Telegram ✓
- AC-116: sync progress counts every chat, archived ones included, and resumes after a restart ✓
- AC-121: chat changes reach the account within a minute ✓
- AC-117: a session ended in Telegram → Session lost and "Sign in again"; the same account comes back as the same Linked Account ✓ (5-minute bound: spec §8 Q3, checked at the real-account run)
- AC-122: an outage → Reconnecting, then Connected with no action; no banner ✓
- AC-36: accounts reconnect after a restart without a code ✓
- AC-118: a session that can't be restored shows Session lost while the others reconnect ✓
- AC-119: without Telegram credentials, Connect Telegram says linking isn't set up ✓
- AC-120: with credentials, the wizard starts and the Operator sees no Owner data ✓ (Operator half N/A by design)

## Design

- Spec: `docs/features/telegram-link/spec.md`
- Architecture: `docs/features/telegram-link/sad.md`
- Decisions: `docs/features/telegram-link/adr/` (ADR-0001 to ADR-0005)
- Data model and migrations: `docs/features/telegram-link/data-model.md`. Migrations: `V202610051800__create_owner_key`, `V202610051801__create_linked_account`, `V202610051802__create_channel`.
- API: `docs/features/telegram-link/contracts/openapi.yaml`, plus `events.md`
- Reviews: `docs/features/telegram-link/_review/`. The fourteenth pass (`review-2026-10-05-r6.md`) is a **PASS**.

## Tasks (SDD-Task trailers)

There are 96 tasks (T1–T96) in `docs/features/telegram-link/tasks.json`, all `done`. T1–T25 come from the breakdown; T26–T96 are follow-ups from fourteen independent review passes. `git log --grep SDD-Task origin/master..telegram-link` lists the per-task commits.

## Verification

- **Local gate:** `./gradlew build integrationTest` on `2d02b99`: `BUILD SUCCESSFUL in 23m 58s`. This covers unit, integration (Testcontainers pgvector), detekt, ktlint, `ModularityTest`, `MigrationRollbackIT`, tsc, ESLint, Prettier and Vitest. The commit after it changes only `.claude/commands/fix-loop.md`.
- **CI on `2d02b99`:**
  - Build, test, lint: passed.
  - End-to-end (Playwright): passed.
  - A flaky Vitest test that comes from master ("Settings lists Models") failed once and passed on rerun. It is raised for @drnetua in this PR.
- **Ran the feature:** I ran the built app jar on the host with the `e2e` profile and the `fake` Telegram adapter, against Postgres, Mailpit and the OpenRouter fake from compose, plus a second installation on :8081 without Telegram credentials.
  - Playwright `telegram-link.spec.ts` and `live-signals.spec.ts`: **52/52 passed**, at phone and desktop widths. Spot checks:
    - **AC-01:** linked an account with two-step verification. It showed Connected with its name and masked phone and started syncing in the Inbox.
    - **AC-02:** Telegram's wait ended the attempt with a countdown; starting again with the same number showed the remaining wait.
    - **AC-111:** unlinking a connected account said it was unlinked and returned to Connect Telegram.
    - **AC-117, AC-122, AC-113:** a lost session showed Session lost and the banner; signing in again brought the account back Connected; an outage showed Reconnecting, then Connected, with no banner.
    - **AC-119:** the installation without credentials said linking isn't set up and opened no wizard.
  - Database after the run:
    - each `linked_account` stores only `phone_country_code` and `phone_last_digits`;
    - `tdlib_key_sealed` and `owner_key.sealed_key` are 60 bytes (nonce + 32-byte key + GCM tag), not plaintext;
    - `channel` holds the synced chats.
- **Deferred to a real Telegram account** (spec §8):
  - a real sign-in on the test servers;
  - a 500-chat sync within 60 s;
  - the 50-account load and memory budget;
  - Session lost within 5 min (Q3);
  - the teleX device gone from active sessions after an unlink (Q4);
  - the late-confirmation flash (Q5);
  - resend refusals (H-W21);
  - an empty `countryCallingCode` on test-DC numbers;
  - a manual screen-reader check of the SCR-02 wait announcement.

## Operational notes

- **Migrations:** the three migrations above run on deploy, after model-profiles'. Rollbacks `U202610051800–1802` drop the Owner keys, Linked Accounts and chat lists.
- **Config:**
  - linking needs `TELEX_TELEGRAM_API_ID`, `TELEX_TELEGRAM_API_HASH` and `TELEX_MASTER_KEY`; without them teleX starts and linking says it isn't set up;
  - optional: `TELEX_TELEGRAM_MAX_ACCOUNTS_PER_OWNER` (default 3);
  - `TELEX_MASTER_KEY_RESET=true` is only for recovering from a lost key;
  - `TELEX_TELEGRAM_ADAPTER=fake` is for e2e only.
- **Keep the master key safe:** losing it loses every linked session.
- **JVM:** needs `--enable-native-access=ALL-UNNAMED` (set in the Dockerfile). TDLib natives ship for linux x64/arm64 and macOS arm64.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
