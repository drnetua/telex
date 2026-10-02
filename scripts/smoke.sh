#!/usr/bin/env bash
# AC-33 smoke: the one command from the README brings up the sign-in page and the local mailbox,
# and a sign-in email reaches Mailpit. Rerunnable by anyone. Env: SMOKE_TIMEOUT (s, default 300),
# SMOKE_KEEP=1 to leave the stack running, SMOKE_DOWN_VOLUMES=1 to start from `down -v`.
set -uo pipefail
cd "$(dirname "$0")/.."

APP=http://localhost:8080
MAILBOX=http://localhost:8025
TIMEOUT="${SMOKE_TIMEOUT:-300}"
RECIPIENT="smoke@example.test"
fail() { echo "SMOKE FAIL: $*" >&2; exit 1; }

# Phase 1: the artifacts the one command needs (fail fast, before any docker work).
for f in Dockerfile .dockerignore compose.yaml README.md; do
  [ -f "$f" ] || fail "missing $f"
done
grep -q 'mailpit' compose.yaml || fail "compose.yaml has no mailpit service"
grep -Eq '^  app:' compose.yaml || fail "compose.yaml has no app service"
grep -q 'docker compose up' README.md || fail "README.md does not name the one command (docker compose up)"
grep -q 'http://localhost:8080' README.md || fail "README.md does not name the app address"
grep -q 'http://localhost:8025' README.md || fail "README.md does not name the mailbox address"
for s in TELEX_PUBLIC_URL TELEX_MAIL_ TELEX_DB_PORT; do
  grep -q "$s" README.md || fail "README.md does not mention $s"
done

# Phase 2: the stack.
[ "${SMOKE_DOWN_VOLUMES:-0}" = 1 ] && docker compose down -v
docker compose up -d --build || fail "docker compose up failed"
trap '[ "${SMOKE_KEEP:-0}" = 1 ] || docker compose down' EXIT

deadline=$(( $(date +%s) + TIMEOUT ))
wait_for() { # description, command...
  local what=$1; shift
  until "$@" >/dev/null 2>&1; do
    [ "$(date +%s)" -lt "$deadline" ] || fail "timed out waiting for $what"
    sleep 3
  done
  echo "ok: $what"
}

wait_for "sign-in page 200 at $APP/sign-in" \
  bash -c "[ \"\$(curl -s -o /dev/null -w '%{http_code}' $APP/sign-in)\" = 200 ]"
wait_for "mailbox page at $MAILBOX" curl -fsS "$MAILBOX/"

# Phase 3: request a sign-in email and read it through Mailpit's API.
jar=$(mktemp); trap 'rm -f "$jar"; [ "${SMOKE_KEEP:-0}" = 1 ] || docker compose down' EXIT
curl -fsS -c "$jar" -o /dev/null "$APP/sign-in" || fail "cannot load sign-in page"
token=$(awk '$6=="XSRF-TOKEN"{print $7}' "$jar")
[ -n "$token" ] || fail "no XSRF-TOKEN cookie"
curl -fsS -b "$jar" -H "X-XSRF-TOKEN: $token" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$RECIPIENT\"}" "$APP/api/v1/sign-in/grants" -o /dev/null \
  || fail "requestSignInEmail did not answer 2xx"
wait_for "an email for $RECIPIENT in Mailpit" \
  bash -c "curl -fsS $MAILBOX/api/v1/messages | grep -q '$RECIPIENT'"

echo "SMOKE OK"
