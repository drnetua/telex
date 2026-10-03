---
status: Draft
owner: "Anton Husiev"
reviewers: ["Tech Lead", "Security Lead"]
updated_at: "2026-10-03"
feature_size: "M"
---

# Spec — telegram-link

> **Glossary:** [CONTEXT](../../../CONTEXT.md) (repo root; no feature-level CONTEXT.md)
> **Reference module / docs / channels used:** `docs/docs/02-epics.md` §E02 + shared DoD · `docs/docs/03-product-spec.md` F1, SCR-02, SCR-60, C-32, C-33, TG-14 · `docs/docs/01-tech-spec.md` §Глосарій, NFR-04, NFR-05, §Ризики · `docs/architecture-map.md` §Constraints · `docs/roadmap.md` step 2, D1 · `docs/teleX-screens/Onb02-Telegram.html` · `docs/features/platform-skeleton/spec.md` (the "Connect Telegram" step, AC-100).

## 1. Context

An Owner can sign in to teleX, but teleX can't see or do anything in their Telegram yet. Every later epic (chat reading, triage, agents, the morning digest) works through the Owner's own Telegram account, signed in as that person. This epic gives the Owner a way to hand teleX that access and to take it back completely. The Owner is the only one who acts here; the Operator only has to enable Telegram linking on the installation once. A Telegram session is a full key to someone's account, so linking has to be easy and unlinking has to be total. When the Owner unlinks, teleX keeps no session and no data for that account, and the teleX device disappears from the account's active sessions in Telegram.

Why now: E01 shipped sign-up and sign-in, and the empty Inbox already shows a single "Connect Telegram" step that leads nowhere. Chat reading (E04), AI consent (E03) and the Owner Bot (E17) all need a Linked Account, so this epic is the only blocker for wave 4. The roadmap also flags the riskiest unknown of the project here (D1). Nobody has yet proven that Telegram's official client library runs on the project's runtime and ships in its image.

Committed approach: the Telegram sign-in the Owner already knows, wrapped in a teleX wizard. The Owner types a phone number, then the code Telegram sends to their other devices, then their two-step verification password if they have one. teleX then holds that account's session encrypted with a per-Owner key. Each Linked Account has its own session and syncs its chat list in the background with visible progress. Linked Accounts come back by themselves after teleX restarts. An Owner may link several accounts up to a limit the Operator sets for the installation. A lost session (ended from the Telegram app) is not an unlink: the account waits for the Owner to sign in to it again, and everything attached to it stays. Unlinking is explicit and total. Success means a new Owner goes from the "Connect Telegram" step to a connected account with a syncing chat list in about two minutes. It also means a restart never asks for a code again, and an unlink leaves nothing behind, checked by a dump of the stored data and by the account's active sessions in Telegram.

Traceability:
- Epic E02 features 1–6 are all in scope. US-02, US-03, AC-01, AC-02, AC-03, AC-04 and AC-36 keep their epic ids. New stories start at US-50 and new criteria at AC-106, after the highest ids in `02-epics.md` and `platform-skeleton`.
- Decision deviation: AC-05 ("unlinking an account with 3 agents pauses all three and cancels their scheduled runs") moves to E09 agent-builder, with the schedule part going to E20. The reason is that agents and schedules don't exist before E09 and E20, and the shared DoD forbids stubs. E02 keeps the cross-context half that exists now: the unlink is announced to every part of teleX that acts through the account (AC-112), and E09 and E20 build the pause on that announcement. `02-epics.md` is patched together with this spec, and the move is recorded in [ADR-0001](./adr/0001-move-agent-pause-on-unlink-to-agent-builder.md).
- Decision deviation: SCR-02 is "Step 2 of 6" of onboarding, but steps 3–6 (consent, private zone, Owner Bot, first agent) arrive with E03, E08, E17 and E09. In E02 the wizard starts from the "Connect Telegram" step on the empty Inbox and from the Accounts page, and returns to the Inbox when it's done.
- Decision deviation: the account limit comes from one installation-wide setting, while E26 AC-74 describes a per-Owner quota. The reason is that the Operator console that edits per-Owner quotas arrives in E26. E02 enforces the limit, and E26 makes it editable per Owner.
- Decision deviation: the unlink confirmation is the ordinary C-33 dialog that names the consequences, not the destructive variant where the Owner types the name. The reason is that before E09 an unlink stops no agents, so typing the name adds friction without protecting anything. E09 can switch to the destructive variant once an unlink stops agents.
- Decision deviation: F1 step 2 shows "Open the teleX bot" right after linking. That button belongs to E17 and is not shown in E02.

## 2. Goals

- An Owner connects their own Telegram account to teleX from the browser, on phone or laptop, using only the sign-in steps Telegram itself asks for, and sees their chats start syncing.
- An Owner's Linked Accounts stay connected through teleX restarts and survive a lost session without losing what is attached to them.
- An Owner can cut teleX off from a Telegram account completely: no session, no stored data and no teleX device left in Telegram.

## 3. Non-goals

- Reading or sending messages. Chat history and live updates are E04, sending is E05; E02 syncs only the chat list.
- Signing in with a QR code from the Telegram app. Phone, code and password is the flow in F1 and SCR-02, and one path keeps the wizard and its tests small. A QR sign-in can be added later without changing the Linked Account.
- Creating a new Telegram account, changing the phone number of one, or recovering a forgotten two-step verification password. These belong to the Telegram app; teleX only explains where to do them.
- Owner Bot notifications, including the "Telegram session lost" service message (TG-14). The bot arrives in E17; in E02 a lost session is shown only in the web (the account state and a Status Banner, AC-122), with no email either, because before E09 nothing uses an account in the background.
- AI consent (E03) and marking a private zone (E08). Nothing in E02 sends any Telegram content to AI.
- Editing the account limit per Owner. It is E26; E02 reads one installation-wide value.
- Pausing agents and cancelling scheduled runs on unlink. This moves to E09 and E20 (see §1 deviation); E02 only announces the unlink.
- Custom names for Linked Accounts. An account is shown by its Telegram name and a masked phone number, which is enough to tell a few accounts apart.

## 4. User stories

### US-02: Link a Telegram account

**As an** Owner
**I want** to link my Telegram account to teleX with my phone number, the code Telegram sends me and my password if I have one
**So that** teleX can work with my chats on my behalf

### US-03: Unlink an account for good

**As an** Owner
**I want** to unlink a Linked Account so that teleX signs out of it and deletes everything it stored for it
**So that** no copy of my Telegram access stays in teleX

### US-50: Link several accounts

**As an** Owner
**I want** to link more than one Telegram account, up to the installation's limit, and see all of them in one list
**So that** my personal and work accounts both work in teleX

### US-51: Know each account's state

**As an** Owner
**I want** to see whether each Linked Account is connected (and whether it is still syncing its chats), is reconnecting after Telegram was unreachable, or has lost its session, and to sign in to it again when it has
**So that** I know when teleX can work with an account and fix it without starting over

### US-52: Stay connected across restarts

**As an** Owner
**I want** my Linked Accounts to come back on their own after teleX restarts
**So that** I never have to type a Telegram code again just because the server restarted

### US-53: Enable Telegram linking

**As an** Operator
**I want** to give the installation its Telegram app credentials once, and to have Owners told plainly when linking isn't set up
**So that** Owners can link accounts without me ever seeing their chats

## 5. Acceptance criteria

### AC-01 (US-02) — happy

**Given** a signed-in Owner with no Linked Account, whose Telegram account has two-step verification turned on
**When** the Owner starts "Connect Telegram", types their phone number, types the code Telegram sent to their other devices and then types their password
**Then** the account is shown as connected as soon as the sign-in completes, with its Telegram name and a masked phone number (only the country code and the last two digits visible), its chat list starts syncing with visible progress, and the Inbox no longer shows the "Connect Telegram" step; an account without two-step verification skips the password step

### AC-02 (US-02) — error

**Given** an Owner in the linking wizard who has been sent a code
**When** they type a wrong or expired code
**Then** the wizard says the code is wrong or expired, lets them try again or ask for a new code, and when Telegram limits the attempts the wizard ends the attempt and says when the Owner can try again, counting down to that time; starting again with the same number before then shows the remaining wait instead of sending a code

### AC-106 (US-02) — error

**Given** an Owner in the linking wizard at the password step
**When** they type a wrong two-step verification password
**Then** the wizard says the password is wrong, shows the password hint the Owner set in Telegram if there is one, lets them try again, and explains that a forgotten password can only be reset in the Telegram app

### AC-107 (US-02) — error

**Given** an Owner in the linking wizard
**When** they type a phone number that isn't a valid number, has no Telegram account, or that Telegram has banned
**Then** the wizard blocks the step and says which one it is in plain language; for a number without a Telegram account it says to create the account in the Telegram app first, and teleX never creates a Telegram account itself

### AC-04 (US-02) — domain invariant

**Given** a Telegram account that is already a Linked Account of another Owner
**When** an Owner completes the linking wizard for that same Telegram account
**Then** teleX blocks the link with the rule "one Telegram account belongs to one Owner", ends the sign-in it just made so no teleX device for the second Owner remains in the account's active sessions in Telegram, and leaves the other Owner's Linked Account untouched

### AC-108 (US-02) — domain invariant

**Given** an Owner whose Telegram account is already one of their Linked Accounts
**When** they go through the linking wizard for the same Telegram account again
**Then** teleX doesn't create a second Linked Account: if that account is connected, teleX ends the sign-in it just made, so no extra teleX device remains in the account's active sessions in Telegram, and tells them the account is already linked; if that account has lost its session, the wizard counts as "Sign in again" and brings back the same Linked Account (AC-117). A Telegram account is recognized by its identity in Telegram, not by its phone number, so a changed phone number still matches

### AC-109 (US-02) — domain invariant

**Given** an Owner who started the linking wizard
**When** they cancel it, or take no step in it for 15 minutes
**Then** the attempt is discarded, no teleX device from it remains in the account's active sessions in Telegram, and starting again begins with the phone number; until then an Owner has at most one open attempt, and reloading the page or opening the wizard in another tab or device continues it at the step where it stopped

### AC-110 (US-02) — cross-context

**Given** an Owner with a connected Linked Account
**When** they sign out of teleX, or their Sign-in Session ends or is revoked
**Then** the Linked Account stays connected and keeps syncing, and the next time they sign in it is still there; and an Owner whose Sign-in Session ends in the middle of the linking wizard must sign in again and start the wizard over, with no session kept from the unfinished attempt

### AC-03 (US-03) — authorization

**Given** two Owners, each with their own Linked Account
**When** one Owner tries to see, re-sign-in to or unlink the other Owner's Linked Account, or to see any chat synced for it, by any means
**Then** teleX behaves as if that account and those chats don't exist; and the Operator sees no Telegram name, phone number or chat of any Owner's Linked Account

### AC-111 (US-03) — happy

**Given** an Owner with a connected Linked Account
**When** they choose to unlink it and confirm in a dialog that names what will happen
**Then** teleX signs out of that Telegram account so the teleX device disappears from the account's active sessions in Telegram, deletes the account's session and every chat-list entry it synced, and removes the account from the list; if it was the Owner's last Linked Account, the Inbox shows the "Connect Telegram" step again

### AC-112 (US-03) — cross-context

**Given** an Owner who unlinks a Linked Account
**When** the unlink completes, and also after teleX restarts, even if it stopped right after the unlink
**Then** the account no longer appears anywhere in teleX that lists or offers the Owner's Linked Accounts and can't be chosen for anything; linking the same Telegram account again creates a new Linked Account with nothing attached; from E09 and E20 on, the same unlink pauses its agents and cancels their scheduled runs (AC-05, moved)

### AC-113 (US-03) — error

**Given** an Owner whose Linked Account has lost its session, or whose Telegram can't be reached at that moment
**When** they unlink it
**Then** teleX still deletes everything it stored for the account and removes it from the list, and tells the Owner that Telegram couldn't confirm the sign-out, so they should check active sessions in the Telegram app

### AC-114 (US-50) — happy

**Given** an Owner with one Linked Account and an installation limit of more than one
**When** they add another account from the Accounts page and complete the wizard
**Then** both accounts appear in the Accounts list, each with its Telegram name, masked phone number and state, and each syncs its own chat list

### AC-115 (US-50) — domain invariant

**Given** an Owner who already has as many Linked Accounts as the installation's limit allows
**When** they try to add another account, or finish a wizard after another account has taken the last place
**Then** teleX doesn't start the wizard, or ends the sign-in it just made so no teleX device remains in Telegram, and tells them the limit and that they can unlink an account to free a place; every Linked Account counts toward the limit, including one that has lost its session, signing in again to a lost-session account takes no new place, and lowering the limit never unlinks existing accounts

### AC-116 (US-51) — happy

**Given** an Owner who just linked an account
**When** its chat list is syncing
**Then** the connected account shows the sync progress as chats synced out of the total, where the total counts every chat of the account, archived ones included, and when the sync finishes it shows the number of chats; the Owner can leave the page and come back without stopping the sync, and if teleX restarts during the sync it continues from where it stopped instead of starting over

### AC-121 (US-51) — happy

**Given** an Owner with a connected Linked Account whose chat list has finished syncing
**When** in Telegram they join or leave a chat, a chat is renamed, or new messages arrive
**Then** within one minute the number of chats shown for the account reflects the change, and the chat list teleX keeps for the account stays current while it is connected; showing that list and the messages is E04

### AC-117 (US-51) — error

**Given** an Owner with a connected Linked Account
**When** the teleX session is ended from the Telegram app or by Telegram itself
**Then** within 5 minutes the account shows "Session lost" with a "Sign in again" action; signing in again with the same Telegram account brings back the same Linked Account with everything attached to it, while signing in with a different Telegram account at that point is refused, that sign-in is ended so no teleX device remains in Telegram, and the Owner is told to link it as a new account (or, if it belongs to another Owner, sees the rule from AC-04)

### AC-122 (US-51) — error

**Given** an Owner with a connected Linked Account
**When** teleX temporarily can't reach Telegram, or Telegram confirms that the account's session has ended
**Then** while Telegram is unreachable the account shows "Reconnecting" on the Accounts page and teleX reconnects on its own without asking the Owner for anything; only when Telegram confirms the session has ended does the account show "Session lost", and then every signed-in screen also shows a Status Banner saying the account is disconnected, with a "Sign in again" action, until the Owner signs in again or unlinks it

### AC-36 (US-52) — happy

**Given** an Owner with 2 connected Linked Accounts
**When** teleX restarts
**Then** both accounts are connected again without the Owner typing any code

### AC-118 (US-52) — error

**Given** an Owner with 2 Linked Accounts, one of whose sessions Telegram ended while teleX was stopped
**When** teleX restarts
**Then** that account shows "Session lost" and the other one is connected again as usual

### AC-119 (US-53) — error

**Given** an installation whose Operator hasn't given it Telegram app credentials
**When** an Owner chooses "Connect Telegram" or "Add account"
**Then** teleX says that Telegram linking isn't set up on this installation yet and that the Operator has to finish the setup, and doesn't start the wizard

### AC-120 (US-53) — happy

**Given** an installation whose Operator has followed the README step that gives it Telegram app credentials
**When** an Owner chooses "Connect Telegram"
**Then** the linking wizard starts, and nothing in the Operator's setup or in what the Operator can see shows any Owner's Telegram name, phone number or chats

## 6. Non-functional requirements

| Aspect | Target | Measurement |
|---|---|---|
| Wizard step response (phone, code, password; excluding Telegram delivering the code to the Owner) | p95 ≤ 3 s per step | timings recorded in integration tests against the in-memory Telegram fake + manual check on a real test account recorded in the E02 pull request |
| Chat list sync after linking | an account with ≤ 500 chats (archived included) is fully synced within 60 s; later changes show within 60 s (AC-121) | manual timed run on a real test account, recorded in the E02 pull request |
| Lost session detection | an account shows "Session lost" ≤ 5 min after its session is ended in Telegram, and a Telegram outage never shows "Session lost" | integration test with a controlled clock + manual check on a test account |
| Reconnect after restart | every Linked Account with a valid session is connected again ≤ 60 s after teleX is ready | integration test (2 accounts) + manual restart on a test account |
| Linked Accounts per instance (4 vCPU / 8 GB, tech spec NFR-04) | ≥ 50 connected at once | one-off load run with 50 accounts on Telegram's test servers, recorded in the E02 pull request |
| Unlink announcement survives a restart | 100% of unlinks are delivered to every part of teleX that acts through the account, including when teleX stops right after the unlink (tech spec NFR-06) | integration test that stops the app after the unlink and checks delivery after restart |
| Session data at rest (tech spec NFR-05) | 100% of stored session data unreadable without the Owner's key | dump of the stored data checked for plaintext session material (E02 DoD) |
| Unlink leaves nothing | 0 stored items that identify the Telegram account (session, Telegram account id, phone number, name, chat list) for an unlinked account, always; 0 active teleX devices in Telegram whenever Telegram is reachable at unlink time (otherwise AC-113 applies and nothing is kept for a later retry). Records that carry only teleX's internal id of the Linked Account and no Telegram data (events, metrics) are kept | dump of the stored data + the account's active sessions list in Telegram, recorded in the E02 pull request |
| Responsive + accessible | the wizard, the Accounts page and the unlink dialog work at 360 px and 1280 px and meet WCAG 2.2 AA | Playwright at both widths + automated accessibility scan with 0 violations |
| Availability | N/A — self-hosted single instance, no SLO in E02 | — |

## 6.1 Security / privacy

- **Data classification:** confidential. A Telegram session gives full access to a person's Telegram account, so it is the most sensitive thing teleX stores.
- **Personal data touched:** per Linked Account: phone number, Telegram account id and display name, the Telegram session, and the synced chat list (chat titles, types, folders and unread counts).
- **AuthZ/AuthN impact:** every linking, re-sign-in, listing and unlink action requires a live Sign-in Session and only ever looks among the caller's own Linked Accounts (AC-03). The Operator has no view of any Linked Account.
- **Secrets:** the login code and the two-step verification password are passed to Telegram and never stored, shown back or logged. The session is stored only encrypted with a per-Owner key (NFR-05).
- **Abuse cases:**
  - Cross-owner access to a Linked Account: denied by hiding its existence (AC-03), so an Owner can't even confirm another Owner's account is there.
  - Linking an account that another Owner already linked: blocked (AC-04). This tells the second person that the account is used somewhere in this installation, which is accepted because they had to receive its code to get there.
  - Someone who holds an Owner's teleX Sign-in Session links their own Telegram to the Owner's teleX: harmless to the Owner's own accounts, and the account limit caps it.
  - Guessing codes or passwords: Telegram's own attempt limits apply and are surfaced as a wait (AC-02); teleX adds no retries of its own.
  - Unlinking leaves a working session behind: prevented by signing out in Telegram and deleting locally even when Telegram can't confirm (AC-111, AC-113).
- **Security review:** Required. This epic adds the first stored third-party credential (the Telegram session) and new personal data. It is done as a security-focused pass inside `/sdd:review`.

## 7. Metrics / KPIs

- **Link completion** (linking wizards started → accounts connected; counted from the app's own events, without phone numbers or names) — baseline: 0 (no linking exists); target: ≥ 80% within the first 30 days of real use on the course installation.
- **Time to link** (from "Connect Telegram" to the completed sign-in that makes the account connected, median; same events) — baseline: none; target: ≤ 2 min within the first 30 days.
- **Restart survival** (Linked Accounts with a valid session that are connected again after a restart ÷ all such accounts) — baseline: none; target: 100% on every restart in the first 30 days.
- **Unlink completeness** (unlinked accounts with any stored item or active teleX device left) — baseline: none; target: 0, checked on the run recorded when the epic ships.

## 8. Open questions

- [ ] What is the default account limit per Owner? Default now: 3, one installation-wide setting in the installation config; E26 makes it editable per Owner. — owner: Anton Husiev (PM), due: before `/sdd:design telegram-link`
- [ ] Does Telegram's official client library build and run on the project's runtime, and can it ship in the app's image (roadmap D1)? Default now: a spike is the first task of E02, before any wizard work. — owner: agent (spike), due: before `/sdd:design telegram-link`
