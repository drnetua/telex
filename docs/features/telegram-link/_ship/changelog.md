# Changelog — telegram-link

## telegram-link — Owners link, watch and fully unlink their Telegram accounts, and sessions survive restarts

**What:** an Owner can now connect their own Telegram accounts to teleX and see them working.

- **Connect Telegram** (SCR-02) is a wizard with these steps: phone number → Sign-in Code → two-step password (only when the account has one). It explains every Telegram refusal in plain language:
  - a wrong or expired code, with the option to retry or send a new one;
  - Telegram's wait, as a countdown to a time in the Owner's own zone;
  - a wrong password, with the Owner's hint and a note on how to reset it;
  - an invalid, unregistered or banned number.

  Reloading the page or opening a second tab continues the same attempt. Cancel starts over.
- **Linked Accounts.** Each account shows its Telegram name, a masked phone number (country code and last two digits only) and a state with an icon and words: Connected, Reconnecting or Session lost. While connected it shows chat sync progress ("N of total", archived chats included) and then the number of chats. Changes in Telegram show up within a minute. Up to 3 accounts per Owner by default.
- **Staying connected.** Accounts reconnect by themselves after a restart, with no code to type. A Telegram outage shows "Reconnecting", never "Session lost". A session ended in Telegram shows "Session lost" with "Sign in again" on the account and in the shell's Status Banner. Signing in again with the same account brings back the same Linked Account.
- **Unlink.** Unlinking signs teleX out of the account, so its device leaves the account's active sessions in Telegram. It deletes the session and every synced chat entry. If Telegram can't confirm the sign-out, teleX still deletes everything and tells the Owner to check active sessions in the Telegram app.
- **Privacy.** No other Owner and no Operator ever sees an Owner's Telegram name, phone number or chats.

**Why:** everything else in teleX works inside the Owner's Telegram account, so linking it safely and keeping it linked is the foundation ([spec](../spec.md) §1, §2). Key decisions:

- [ADR-0002](../adr/0002-own-linked-accounts-and-their-chat-list-in-messaging-behind-a-session-only-telegram-acl.md): `messaging` owns Linked Accounts and their chat list. `telegram` is a session-only ACL, the only module that touches TDLib.
- [ADR-0003](../adr/0003-seal-each-tdlib-database-key-with-a-stored-per-owner-key-under-an-installation-master-key.md): envelope encryption. An installation master key wraps a per-Owner key, which seals each TDLib database key. Deleting the sealed key makes any leftover session file unreadable.
- [ADR-0004](../adr/0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md): TDLight Java behind the `TdlibFacade`, with a `fake` adapter so every AC is testable without Telegram.
- [ADR-0005](../adr/0005-push-live-state-to-the-spa-as-sse-invalidation-hints.md): live state reaches open pages as SSE invalidation hints. REST stays the only data path.
- [ADR-0001](../adr/0001-move-agent-pause-on-unlink-to-agent-builder.md): pausing an account's agents on unlink moves to the agent builder (E09).

**How to use:**
- **Operator:** create an app at <https://my.telegram.org> and set:
  - `TELEX_TELEGRAM_API_ID` and `TELEX_TELEGRAM_API_HASH`;
  - `TELEX_MASTER_KEY`: generate it once with `openssl rand -base64 32`, and keep a copy off the server;
  - optionally `TELEX_TELEGRAM_MAX_ACCOUNTS_PER_OWNER` (default 3).

  Until the credentials are set, teleX starts normally, and Connect Telegram says linking isn't set up yet. See the [README](../../../../README.md) "Telegram setup".
- **Owner:** Inbox → Connect Telegram, or Settings → Accounts to add, sign in again to, or unlink an account. The API is `/api/v1/linking-attempt` (and its `phone`, `code`, `code/resend`, `password` steps) and `/api/v1/linked-accounts` ([openapi.yaml](../contracts/openapi.yaml)). Live hints arrive on the existing SSE stream ([events.md](../contracts/events.md)).

**Operational notes:**
- **Migrations:**
  - `V202610051800__create_owner_key`, `V202610051801__create_linked_account` and `V202610051802__create_channel` are applied on deploy, after model-profiles' `V202610031200–1203`.
  - Rollbacks are `U202610051800–1802` (`MigrationRollbackIT` runs up → down → up).
  - They were renumbered from `…031200–1202` when the branch was rebased, because those numbers clashed with model-profiles.
- **Config:**
  - Required for linking: `TELEX_TELEGRAM_API_ID`, `TELEX_TELEGRAM_API_HASH` and `TELEX_MASTER_KEY`.
  - `TELEX_MASTER_KEY_RESET=true` exists only to recover from a lost key, and discards every sealed key.
  - `TELEX_TELEGRAM_ADAPTER=fake` is for e2e only, never for real use.
- **Runtime:** the JVM needs `--enable-native-access=ALL-UNNAMED`; the Dockerfile sets it. The TDLib natives ship for linux x64/arm64 and macOS arm64. Session directories live under `TELEX_TELEGRAM_SESSIONS_DIR` (a volume in `compose.yaml`).
- **Losing the master key loses every linked session:** every Owner has to sign in to Telegram again.
- **Rollback:** revert the deploy and run the three `U…` rollbacks. This drops the Linked Accounts, chat lists and Owner keys, and the TDLib session directories become unreadable.
- **Checks still to do on a real Telegram account** (spec §8 and review-2026-10-05-r4):
  - a real sign-in;
  - a 500-chat sync within 60 s;
  - the 50-account load;
  - Session lost within 5 min (Q3);
  - the device gone from active sessions after an unlink (Q4);
  - the late-confirmation flash (Q5);
  - resend refusals (H-W21);
  - an empty `countryCallingCode` on test-DC numbers;
  - a screen-reader check of the wait announcement.

**Acceptance criteria delivered:** AC-01, AC-02, AC-03, AC-04, AC-36, AC-106 – AC-122 (US-02, 03, 50–53). The Operator halves of AC-03 and AC-120 are N/A by design: the Operator has no view of Owner data.
