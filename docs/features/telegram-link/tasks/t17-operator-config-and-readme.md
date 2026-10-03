---
id: T17
title: "Wire the Operator config, the session volume and the README Telegram setup step"
layer: "wiring"
deps: ["T3", "T6"]
blocks: ["T25"]
acs: ["AC-119", "AC-120"]
files_hint: ["backend/app/src/main/resources/application.yaml", "backend/app/src/main/resources/application-local.yaml", "compose.yaml", "README.md", "docs/docs/01-tech-spec.md", "docs/features/telegram-link/sad.md"]
owner: "Anton Husiev"
estimate: "S"
context_budget: "M"
status: "todo"
---

# T17 — Wire the Operator config, the session volume and the README Telegram setup step

## Place in the sequence

- **Blocked by:** T3 — Add OwnerKeys envelope encryption with the master-key check and reset, and SignInSessions.isLive, T6 — Implement the tdlight adapter: TDLib auth states, errors and chat list mapped to the port · **Blocks:** T25 — Add Playwright end-to-end tests of linking, live state and unlink at 360 px and 1280 px with axe · **Wave:** 4 — needs the master-key settings (T3) and the real adapter (T6) to document.
- **Lane:** shares `docs/docs/01-tech-spec.md` with T1 — serialized.

## Why (user story)

> **As an** Operator
> **I want** to give the installation its Telegram app credentials once, and to have Owners told plainly when linking isn't set up
> **So that** Owners can link accounts without me ever seeing their chats
>
> — `spec.md §4, US-53, verbatim` · full text: [spec.md](../spec.md)

This task gives the Operator one README step to enable linking, without ever seeing an Owner's Telegram data.

## Inlined context

> Operator config (README step, US-53):
> - `TELEX_TELEGRAM_API_ID` and `TELEX_TELEGRAM_API_HASH`, from my.telegram.org.
> - `TELEX_MASTER_KEY`: 32 random bytes, base64. The README shows `openssl rand -base64 32` and warns that losing it loses every session (ADR-0003).
> - `TELEX_TELEGRAM_MAX_ACCOUNTS_PER_OWNER`, default 3.
> When the API credentials are missing, or the master key is missing on an installation that has never stored an Owner key, linking reports "isn't set up" (AC-119), and the app still starts. […] Recovery from a truly lost key is explicit: start once with `TELEX_MASTER_KEY_RESET=true` and the new key.
> Session files: named volume `telex-tdlib` at `/var/lib/telex/tdlib` (`TELEX_TELEGRAM_SESSIONS_DIR`). It is backed up together with Postgres or not at all.
> Local and CI: `compose.yaml` gains the volume and passes the variables. […] `bootRun --spring.profiles.active=local` uses `fake` unless real credentials are set.
>
> — `sad.md §7, Operator config + Session files + Local and CI, abridged` · full text: [sad.md](../sad.md)

> The `fake` adapter is production code in `telegram`, selected only by configuration. The README states that it is never for real use.
> Operator access to the database and master key […] The README tells Owners that the installation's operator is trusted.
>
> — `adr/0004 §Consequences + sad.md §11, abridged` · full text: [0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md](../adr/0004-use-tdlight-java-behind-the-tdlib-facade-with-a-fake-telegram-adapter.md)

> **This deviates from sad §8 Events**, which routes all six events through the JDBC registry. Patch sad §8 Events and the §5 note "Publishes session state and chat events" when `implement` touches the SAD.
> The tech-spec event table names `telegram` as the publisher of `AccountLinked` / `AccountUnlinked`.
>
> — `contracts/api-sync-report.md §C 5 + sad.md §11 Docs drift, abridged` · full text: [api-sync-report.md](../contracts/api-sync-report.md)

> **Hard rule:** Nothing in the Operator's setup or in what the Operator can see shows any Owner's Telegram name, phone number or chats.
>
> — `spec.md §5, AC-120, abridged` · full text: [spec.md](../spec.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [events.md](../contracts/events.md) · [screens.md](../screens.md) ·
[adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes.

## API contract

Internal — no API surface.

## Acceptance criteria

### AC-119 — error

> **Given** an installation whose Operator hasn't given it Telegram app credentials
> **When** an Owner chooses "Connect Telegram" or "Add account"
> **Then** teleX says that Telegram linking isn't set up on this installation yet and that the Operator has to finish the setup, and doesn't start the wizard
>
> — `spec.md §5, AC-119, verbatim` · full text: [spec.md](../spec.md)

### AC-120 — happy

> **Given** an installation whose Operator has followed the README step that gives it Telegram app credentials
> **When** an Owner chooses "Connect Telegram"
> **Then** the linking wizard starts, and nothing in the Operator's setup or in what the Operator can see shows any Owner's Telegram name, phone number or chats
>
> — `spec.md §5, AC-120, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `application.yaml`: `telex.telegram.{adapter, api-id, api-hash, sessions-dir, max-accounts-per-owner}`, `telex.master-key`, `telex.master-key-reset` from the `TELEX_*` variables
- [ ] `application-local.yaml`: `adapter: fake` unless `TELEX_TELEGRAM_API_ID` is set; a local sessions dir
- [ ] `compose.yaml`: `telex-tdlib` volume at `/var/lib/telex/tdlib`, pass the new variables
- [ ] `README.md` Operator step: my.telegram.org credentials, `openssl rand -base64 32`, losing the key loses every session, back the volume up with Postgres, the reset flag, the limit, the fake adapter is never for real use, the operator is trusted
- [ ] Patch `docs/docs/01-tech-spec.md` event table (publisher `messaging`) and `sad.md` §8 Events + §5 note (telegram events in-process, non-durable)
- [ ] Smoke IT: app starts with no Telegram credentials and no master key; `startMyLinkingAttempt` → `503 telegram-linking-not-set-up` (AC-119)

## Edge cases

| Case | Behaviour |
|---|---|
| Credentials set, master key missing, no Owner key yet | Starts; "not set up" (AC-119) |
| Master key missing after keys exist | Refuses to start (T3) |

## Definition of Done

- [ ] the smoke IT passes; `docker compose up` brings the app up with the volume
- [ ] README step reviewed against sad §7; no Telegram data in any config or log line it produces
- [ ] every Hard Rule inlined above still holds
