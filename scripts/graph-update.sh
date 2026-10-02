#!/usr/bin/env bash
# Incrementally refresh the graphify knowledge graph in graphify-out/ after a merge into master:
# code is re-parsed (tree-sitter, no LLM), changed docs/images go to Gemini via OpenRouter, then
# communities are re-clustered and re-named. Commits nothing — CI (.github/workflows/graph.yml)
# or you commit the result.
#
# Env: GEMINI_API_KEY (OpenRouter key), GEMINI_BASE_URL, GRAPHIFY_GEMINI_MODEL — defaults below.
# GRAPH_ANY_BRANCH=1 skips the master-only guard (the graph is only ever updated on master, so
# feature branches never conflict on graph.json).
set -euo pipefail
cd "$(dirname "$0")/.."

GRAPHIFY_VERSION=0.9.74
export GEMINI_BASE_URL="${GEMINI_BASE_URL:-https://openrouter.ai/api/v1}"
export GRAPHIFY_GEMINI_MODEL="${GRAPHIFY_GEMINI_MODEL:-google/gemini-3-flash-preview}"
fail() { echo "GRAPH UPDATE FAIL: $*" >&2; exit 1; }

[ -n "${GEMINI_API_KEY:-}" ] || fail "GEMINI_API_KEY is not set (OpenRouter key, sk-or-...)"
command -v graphify >/dev/null || fail "graphify not installed: uv tool install 'graphifyy[gemini,sql]==$GRAPHIFY_VERSION'"
installed="$(uv tool list 2>/dev/null | awk '$1 == "graphifyy" { print $2 }')"
[ -z "$installed" ] || [ "$installed" = "v$GRAPHIFY_VERSION" ] \
  || echo "warning: graphifyy $installed installed, pinned $GRAPHIFY_VERSION (cache keys are per version)" >&2

branch="$(git rev-parse --abbrev-ref HEAD)"
if [ "$branch" != master ] && [ "${GRAPH_ANY_BRANCH:-0}" != 1 ]; then
  fail "on '$branch' — update the graph on master only (GRAPH_ANY_BRANCH=1 to override)"
fi

graphify extract .   # incremental: manifest.json decides what is new/changed/deleted
graphify label .     # re-cluster, name communities with Gemini, rewrite GRAPH_REPORT.md + graph.html
