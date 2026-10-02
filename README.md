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
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`: the database.
- Behind a reverse proxy, send `X-Forwarded-*` headers; the app uses `server.forward-headers-strategy=framework`.
