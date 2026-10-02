---
id: T19
title: "Make teleX start with one command: Dockerfile, compose with app and Mailpit, README and a smoke script"
layer: "docs"
deps: ["T9", "T14"]
blocks: ["T20"]
acs: ["AC-33"]
files_hint: ["Dockerfile", ".dockerignore", "compose.yaml", "README.md", "scripts/smoke.sh"]
owner: "Anton Husiev"
estimate: "M"
context_budget: "M"
status: "todo"
---

# T19 — Make teleX start with one command: Dockerfile, compose with app and Mailpit, README and a smoke script

## Place in the sequence

- **Blocked by:** T9 — Expose the sign-in REST endpoints: request email, preview and redeem link, redeem code, sign out, T14 — Build Sign in (SCR-01, email part) and Check your email (SCR-07) with the Sign-in Code · **Blocks:** T20 — Add Playwright end-to-end tests at 360 px and 1280 px with an accessibility scan and a virtual authenticator · **Wave:** 5 — needs the sign-in endpoint (T9) and the SCR-01 page (T14) to have something to start and smoke-test.
- **Lane:** own lane (only task touching `compose.yaml`, `Dockerfile`, `README.md`).

## Why (user story)

> **As an** Operator
> **I want** to start a complete teleX installation with one command from the README
> **So that** the installation needs no manual setup before the first person can sign in
>
> — `spec.md §4, US-41, verbatim` · full text: [spec.md](../spec.md)

This task is the one command: a clean machine with Docker gets a working sign-in page and a local mailbox.

## Inlined context

> **Local (AC-33).** `docker compose up` starts three services:
> - `app`, built by a new multi-stage `Dockerfile`: a build stage on JDK 25 + Node/pnpm runs the Gradle `bootJar`, including the SPA; a runtime stage on JRE 25 runs it. The app is reachable at `http://localhost:8080`.
> - `postgres` (`pgvector/pgvector:pg17`, host port `TELEX_DB_PORT`, default 5432).
> - `mailpit`, the local mailbox: SMTP on 1025 for the app, a web page on `http://localhost:8025` for the Operator.
>
> The app waits for Postgres's health check, and Flyway migrates on start. The README names both addresses. The first image build is outside the 5-minute budget (spec §6).
> **Production (out of E01 scope beyond configuration).** […] The Operator sets `TELEX_PUBLIC_URL=https://<domain>` (ADR-0006), real SMTP settings (`TELEX_MAIL_*` → `spring.mail.*`) and the datasource. […] The README lists exactly these settings and warns against public exposure before E26 (spec §3, §8 OQ-2).
> **Developer loop (unchanged).** `docker compose up -d postgres mailpit` plus `bootRun --spring.profiles.active=local`, and `pnpm dev` with the Vite proxy extended to `/webauthn/**` and `/login/webauthn`.
>
> — `sad.md §7, Topology, abridged` · full text: [sad.md](../sad.md)

> **How verify:** spec §6 measurement, a manual timed run on a clean machine recorded in the E01 pull request. Plus a CI-free smoke script (`docker compose up` → poll the sign-in page → request an email → read it through Mailpit's API) that anyone can rerun.
>
> — `sad.md §10, QG-2, abridged` · full text: [sad.md](../sad.md)

> | One-command install to working sign-in page | ≤ 5 min from the command to the sign-in page on a clean machine with Docker and ≥ 50 Mbit/s, not counting the first build of the teleX application image | manual timed run on a clean machine, recorded in the E01 pull request |
>
> — `spec.md §6, NFR row 1, verbatim` · full text: [spec.md](../spec.md)

> README also: Passkeys work on `localhost` or HTTPS (not over LAN HTTP); `TELEX_PUBLIC_URL` is "choose once" because Passkeys are bound to its host.
>
> — `sad.md §11, risks «No passkeys over LAN HTTP» + «Passkeys are bound to the RP ID», abridged` · full text: [sad.md](../sad.md)

**Fallback:** insufficient or contradicted by the code → read the named file in full
([spec.md](../spec.md) · [sad.md](../sad.md) · [data-model.md](../data-model.md) ·
[openapi.yaml](../contracts/openapi.yaml) · [screens.md](../screens.md) · [adr/](../adr/)) and follow it. Do not guess.

## Data delta

No DB changes (Flyway applies T1's migrations on app start).

## API contract

Internal — no new API. The smoke script calls `requestSignInEmail` (`POST /api/v1/sign-in/grants`, with the `XSRF-TOKEN` cookie echoed in `X-XSRF-TOKEN`) and Mailpit's `GET /api/v1/messages`.

## Acceptance criteria

### AC-33 — happy

> **Given** a clean machine that has only Docker and a copy of the repository
> **When** the Operator runs the one command from the README
> **Then** within 5 minutes (not counting the first build of the teleX application) the address named in the README, opened by the Operator in their browser, shows the sign-in page; the README names a local mailbox page where every email teleX sends shows up, and the Operator can then complete a first sign-in with the email from that mailbox (the sign-in itself is outside the 5 minutes)
>
> — `spec.md §5, AC-33, verbatim` · full text: [spec.md](../spec.md)

## Checklist

- [ ] `Dockerfile` — build stage (JDK 25 + Node + pnpm via corepack, `./gradlew :backend:app:bootJar -x test`), runtime stage on JRE 25; `.dockerignore` excludes `build/`, `node_modules/`, `frontend/dist/`.
- [ ] `compose.yaml` — add `app` (`depends_on: postgres: condition: service_healthy`, `mailpit`; env `SPRING_DATASOURCE_*`, `TELEX_MAIL_HOST=mailpit`, `TELEX_MAIL_PORT=1025`, `TELEX_PUBLIC_URL` default `http://localhost:8080`; port 8080) and `mailpit` (`axllent/mailpit`, ports 1025 + 8025); keep `postgres` as is.
- [ ] `README.md` — the one command, the two addresses, first sign-in via the local mailbox, the developer loop, production settings (`TELEX_PUBLIC_URL`, `TELEX_MAIL_*`, datasource, `server.forward-headers-strategy`), "choose `TELEX_PUBLIC_URL` once", passkeys need `localhost` or HTTPS, and "don't expose publicly before E26".
- [ ] `scripts/smoke.sh` — `docker compose up -d`, poll `http://localhost:8080/sign-in` until 200, fetch the CSRF cookie, request an email for `smoke@example.test`, poll Mailpit until one message for that address exists; non-zero exit on timeout.
- [ ] Run it once locally; time the cold `docker compose up` (excluding the first image build) for the E01 PR.

## Edge cases

| Case | Behaviour |
|---|---|
| Port 5432 taken | `TELEX_DB_PORT` documented and honoured |
| Postgres slow to start | app waits for `service_healthy`; no crash loop |
| Second `docker compose up` | data volume kept; migrations already applied |
| Mailpit down | sign-in email request answers `503`; README says to check the mailpit service |

## Definition of Done

- [ ] `scripts/smoke.sh` exits 0 from a clean `docker compose down -v`.
- [ ] README review: every address and setting above is present.
- [ ] The timed run (≤ 5 min, first build excluded) is noted for the E01 PR description.
- [ ] every Hard Rule inlined above still holds (no secrets committed; defaults are local-only).
