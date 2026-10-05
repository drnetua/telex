# teleX

A multi-user web Telegram client where each Owner runs their own AI agents inside their Telegram account.

## Run it (one command)

Needs only Docker and a copy of this repository.

```bash
docker compose up
```

The first run builds the application image (several minutes); later starts take well under five minutes.

- teleX: <http://localhost:8080> shows the sign-in page.
- Local mailbox (Mailpit): <http://localhost:8025> shows every email teleX sends.

First sign-in: open http://localhost:8080, enter your email, then open http://localhost:8025, and use the link
or the code from the email. If the email request fails with 503, check that the `mailpit` service is running.

Postgres is published on host port 5432; set `TELEX_DB_PORT` if that port is taken:
`TELEX_DB_PORT=5433 docker compose up`. Data lives in the `postgres-data` volume; `docker compose down -v` wipes it.

Passkeys work on `localhost` or over HTTPS, not over plain HTTP on a LAN address.

Smoke test (starts the stack, requests an email, reads it from Mailpit): `./scripts/smoke.sh`.

## Telegram setup (Operator)

Owners can link Telegram accounts only after you, the Operator, finish this step. Until then teleX starts normally
and linking says it isn't set up on this installation yet.

1. Create an app at <https://my.telegram.org> (API development tools). Set `TELEX_TELEGRAM_API_ID` and
   `TELEX_TELEGRAM_API_HASH` to its values.
2. Generate the master key **once** with `openssl rand -base64 32` and set it as `TELEX_MASTER_KEY` on every start.
   teleX refuses to start with a different key once one has sealed a session key. It seals every Owner's
   Telegram session key. **If you lose it, every linked session is lost** and every Owner must sign in to Telegram
   again. Keep a copy outside the server. Recovery from a truly lost key is explicit: start once with
   `TELEX_MASTER_KEY_RESET=true` and the new key, then remove the flag.
3. Optional: `TELEX_TELEGRAM_MAX_ACCOUNTS_PER_OWNER` (default 3).

```bash
# once: generate the key into a file outside the repository
(umask 077 && openssl rand -base64 32 > ~/telex-master.key)
# every start: the same key
export TELEX_TELEGRAM_API_ID=... TELEX_TELEGRAM_API_HASH=... TELEX_MASTER_KEY="$(cat ~/telex-master.key)"
docker compose up
```

Session files live in the `telex-tdlib` volume (`/var/lib/telex/tdlib`, `TELEX_TELEGRAM_SESSIONS_DIR`). Back it up
together with the Postgres volume or not at all: a session directory without its key in Postgres is unreadable, and
Postgres without the directories sends every account to "Session lost".

What you can see: none of this setup, nor the configuration or logs it produces, contains any Owner's Telegram name,
phone number or chats. Owners should know that the installation's operator is trusted: whoever holds the database and
the master key controls the stored sessions.

`TELEX_TELEGRAM_ADAPTER=fake` (the default under the `local` profile) replaces Telegram with an in-memory stand-in
for development and tests. It is never for real use.

## Developer loop

```bash
docker compose up -d postgres mailpit
./gradlew :backend:app:bootRun --args='--spring.profiles.active=local'
cd frontend && pnpm dev   # Vite on :5173, proxies /api to :8080
```

See `CLAUDE.md` for build commands and conventions.

## Production settings

Do not expose teleX publicly before E26 (hardening). Settings for a real installation:

- `TELEX_PUBLIC_URL=https://<domain>`: the public address. Choose it once: Passkeys are bound to its host.
- `TELEX_MAIL_HOST`, `TELEX_MAIL_PORT`, `TELEX_MAIL_USERNAME`, `TELEX_MAIL_PASSWORD`, `TELEX_MAIL_FROM`:
  real SMTP settings (mapped to `spring.mail.*`).
- AI settings (the model provider is OpenRouter). Without the key teleX still runs; see "Without a provider key" below.

  | Environment variable | Property | Default |
  |---|---|---|
  | `TELEX_OPENROUTER_API_KEY` | `telex.llm.openrouter.api-key` | empty (AI off) |
  | | `telex.llm.openrouter.base-url` | `https://openrouter.ai/api/v1` |
  | | `telex.llm.attempt-timeout` | `60s` |
  | | `telex.llm.catalog.refresh-interval` | `24h` |
  | | `telex.llm.catalog.retry-interval` | `5m` (retry after a failed catalog refresh) |

  The key is an installation secret: set it only through the environment. It is never returned to the browser or logged.
  Only `TELEX_OPENROUTER_API_KEY` has an environment variable; set the other properties through Spring (for example
  `TELEX_LLM_ATTEMPT_TIMEOUT=90s` or `--telex.llm.attempt-timeout=90s`).

- System profile models. The three system profiles (Fast and cheap, Balanced, Careful) ship with these models, one per
  slot:

  | Profile | text | vision | image |
  |---|---|---|---|
  | Fast and cheap (`fast`) | `google/gemini-3.5-flash-lite` | `google/gemini-3.5-flash-lite` | `google/gemini-3.1-flash-lite-image` |
  | Balanced (`balanced`) | `google/gemini-3.5-flash` | `google/gemini-3.5-flash` | `google/gemini-3.1-flash-image` |
  | Careful (`careful`) | `anthropic/claude-sonnet-5` | `anthropic/claude-sonnet-5` | `google/gemini-3-pro-image` |

  To override a slot, set `telex.models.system-profiles.<fast|balanced|careful>.<text|vision|image>` to a list of up to
  three OpenRouter model ids (the first is tried first, the rest are fallbacks). For example, in `application.yaml` or an
  external config file:

  ```yaml
  telex:
    models:
      system-profiles:
        balanced:
          text: [anthropic/claude-sonnet-5, google/gemini-3.5-flash]
  ```

  or `--telex.models.system-profiles.balanced.text=anthropic/claude-sonnet-5,google/gemini-3.5-flash`. An override
  replaces that whole slot (slots you leave out keep the shipped models); more than three ids are ignored (the first
  three are used); a model that is not in the catalog or does not fit the slot is logged as a WARN at start and on each
  catalog refresh.

- Without a provider key: teleX logs a startup WARN naming `TELEX_OPENROUTER_API_KEY` and keeps running (sign-in and
  everything that needs no AI work). Owners see an "AI models aren't set up" banner on the Models page; the three system
  profiles are listed with no model available.
- Real-call smoke check (one real call per slot, text, vision and image, through the Balanced profile; it prints the
  answering model and whether the fallback answered, never the answer text). It is skipped when the key is not set:

  ```bash
  export TELEX_OPENROUTER_API_KEY=<your key>
  ./gradlew :backend:app:integrationTest --tests 'telex.agents.RealProviderSmokeIT'
  ```

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`: the database.
- Behind a reverse proxy, send `X-Forwarded-*` headers; the app uses `server.forward-headers-strategy=framework`.
