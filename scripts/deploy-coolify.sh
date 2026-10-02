#!/usr/bin/env bash
# Deploys the committed HEAD to Coolify on the lhost rig:
#   1. builds the image on lhost (x86_64) from `git archive HEAD` and pushes it to lhost's registry (localhost:5000);
#   2. creates or updates the Coolify service "telex" from deploy/coolify/compose.yaml (app + pgvector + Mailpit);
#   3. deploys it and waits until the app answers on the published port with the new image.
#
# Config: environment variables, or `.deploy.env` in the repo root (git-ignored). COOLIFY_TOKEN is required
# (Coolify → Keys & Tokens → API tokens, with write + deploy permission).
#   DEPLOY_SSH=lhost  LHOST_IP=10.10.10.3  COOLIFY_URL=http://$LHOST_IP:8000
#   APP_PORT=8090  MAIL_UI_PORT=8091  TELEX_PUBLIC_URL=http://$LHOST_IP:$APP_PORT
#   COOLIFY_PROJECT=teleX  COOLIFY_SERVICE=telex  DEPLOY_TIMEOUT=600 (s)
# Later, behind the Cloudflare tunnel: TELEX_PUBLIC_URL=https://tele-x.online (passkeys are bound to its host).
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f .deploy.env ] && { set -a; . ./.deploy.env; set +a; }
DEPLOY_SSH="${DEPLOY_SSH:-lhost}"
LHOST_IP="${LHOST_IP:-10.10.10.3}"
COOLIFY_URL="${COOLIFY_URL:-http://$LHOST_IP:8000}"
APP_PORT="${APP_PORT:-8090}"
MAIL_UI_PORT="${MAIL_UI_PORT:-8091}"
TELEX_PUBLIC_URL="${TELEX_PUBLIC_URL:-http://$LHOST_IP:$APP_PORT}"
COOLIFY_PROJECT="${COOLIFY_PROJECT:-teleX}"
COOLIFY_SERVICE="${COOLIFY_SERVICE:-telex}"
DEPLOY_TIMEOUT="${DEPLOY_TIMEOUT:-600}"
REGISTRY=localhost:5000

fail() { echo "DEPLOY FAIL: $*" >&2; exit 1; }
step() { echo "==> $*"; }
[ -n "${COOLIFY_TOKEN:-}" ] || fail "COOLIFY_TOKEN is not set (env or .deploy.env)"
command -v jq >/dev/null || fail "jq is required"

api() { # method path [json-body]
  local method=$1 path=$2 body=${3:-} out code
  out=$(mktemp)
  code=$(curl -sS -o "$out" -w '%{http_code}' -X "$method" "$COOLIFY_URL/api/v1$path" \
    -H "Authorization: Bearer $COOLIFY_TOKEN" -H 'Accept: application/json' \
    ${body:+-H 'Content-Type: application/json' --data "$body"}) || { rm -f "$out"; fail "$method $path: no answer"; }
  if [[ $code != 2* ]]; then echo "$method $path -> $code: $(cat "$out")" >&2; rm -f "$out"; exit 1; fi
  cat "$out"; rm -f "$out"
}

# 1. Image -------------------------------------------------------------------------------------------------------
[ -z "$(git status --porcelain --untracked-files=no)" ] || echo "warning: uncommitted changes are NOT deployed (HEAD only)"
SHA=$(git rev-parse --short=12 HEAD)
IMAGE="$REGISTRY/telex:$SHA"

if ssh "$DEPLOY_SSH" "curl -fsS http://$REGISTRY/v2/telex/tags/list 2>/dev/null | grep -q '\"$SHA\"'"; then
  step "image $IMAGE already in the registry, skipping build"
else
  step "building $IMAGE on $DEPLOY_SSH"
  git archive --format=tar HEAD | ssh "$DEPLOY_SSH" 'rm -rf ~/telex-build && mkdir -p ~/telex-build && tar -x -C ~/telex-build'
  ssh "$DEPLOY_SSH" "cd ~/telex-build && docker build -t $IMAGE -t $REGISTRY/telex:latest . \
    && docker push $IMAGE && docker push $REGISTRY/telex:latest" || fail "image build/push failed"
fi

# 2. Coolify service ---------------------------------------------------------------------------------------------
COMPOSE_B64=$(sed -e "s|__IMAGE__|$IMAGE|" -e "s|__APP_PORT__|$APP_PORT|" -e "s|__MAIL_UI_PORT__|$MAIL_UI_PORT|" \
  -e "s|__PUBLIC_URL__|$TELEX_PUBLIC_URL|" deploy/coolify/compose.yaml | base64 | tr -d '\n')

SERVICE_UUID=$(api GET /services | jq -r --arg n "$COOLIFY_SERVICE" 'map(select(.name == $n)) | .[0].uuid // empty')
if [ -z "$SERVICE_UUID" ]; then
  SERVER_UUID=$(api GET /servers | jq -r '.[0].uuid // empty')
  [ -n "$SERVER_UUID" ] || fail "Coolify has no server"
  PROJECT_UUID=$(api GET /projects | jq -r --arg n "$COOLIFY_PROJECT" 'map(select(.name == $n)) | .[0].uuid // empty')
  if [ -z "$PROJECT_UUID" ]; then
    step "creating Coolify project $COOLIFY_PROJECT"
    PROJECT_UUID=$(api POST /projects "$(jq -nc --arg n "$COOLIFY_PROJECT" '{name: $n, description: "teleX web Telegram client"}')" | jq -r .uuid)
  fi
  step "creating Coolify service $COOLIFY_SERVICE"
  SERVICE_UUID=$(api POST /services "$(jq -nc --arg p "$PROJECT_UUID" --arg s "$SERVER_UUID" --arg n "$COOLIFY_SERVICE" \
    --arg c "$COMPOSE_B64" '{project_uuid: $p, server_uuid: $s, environment_name: "production", name: $n,
      description: "teleX app + pgvector + Mailpit", docker_compose_raw: $c, instant_deploy: false}')" | jq -r .uuid)
else
  step "updating Coolify service $COOLIFY_SERVICE ($SERVICE_UUID)"
  api PATCH "/services/$SERVICE_UUID" "$(jq -nc --arg c "$COMPOSE_B64" '{docker_compose_raw: $c}')" >/dev/null
fi
[ -n "$SERVICE_UUID" ] || fail "no service uuid"

# 3. Deploy and wait ---------------------------------------------------------------------------------------------
step "deploying $IMAGE"
api GET "/deploy?uuid=$SERVICE_UUID&force=false" >/dev/null

deadline=$(( $(date +%s) + DEPLOY_TIMEOUT ))
until ssh "$DEPLOY_SSH" "docker ps --filter name=app-$SERVICE_UUID --format '{{.Image}} {{.Status}}'" 2>/dev/null \
    | grep -q "^$IMAGE .*(healthy)"; do
  [ "$(date +%s)" -lt "$deadline" ] || fail "app container with $IMAGE not healthy within ${DEPLOY_TIMEOUT}s"
  sleep 5
done
code=$(curl -s -o /dev/null -w '%{http_code}' "http://$LHOST_IP:$APP_PORT/sign-in")
[ "$code" = 200 ] || fail "http://$LHOST_IP:$APP_PORT/sign-in answered $code"

echo "DEPLOY OK: $IMAGE"
echo "  teleX:   http://$LHOST_IP:$APP_PORT   (public URL: $TELEX_PUBLIC_URL)"
echo "  mailbox: http://$LHOST_IP:$MAIL_UI_PORT"
echo "  coolify: $COOLIFY_URL (service $COOLIFY_SERVICE, $SERVICE_UUID)"
