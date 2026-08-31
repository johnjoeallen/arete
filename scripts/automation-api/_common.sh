# Shared config + helpers for the automation-API test scripts.
# Sourced by every script in this directory; not executable on its own.
#
# Override any of these in the environment:
#   ARETE_URL        base URL                  (default http://localhost:6809)
#   ARETE_NAMESPACE  namespace slug            (default default)
#   ARETE_SUBMITTER  submitter label (cookie)  (default curl-test)
#   ARETE_RUN        comma-separated list of <validator>/<policy> combinations
#                    (default "generic-policy/enterprise-grade")
#   ARETE_FAIL_ON    policy | never | error | blocker | score<NN  (default policy)

set -euo pipefail

ARETE_URL="${ARETE_URL:-http://localhost:6809}"
ARETE_NAMESPACE="${ARETE_NAMESPACE:-default}"
ARETE_SUBMITTER="${ARETE_SUBMITTER:-curl-test}"
ARETE_RUN="${ARETE_RUN:-generic-policy/enterprise-grade}"
ARETE_FAIL_ON="${ARETE_FAIL_ON:-policy}"

API="${ARETE_URL}/api/v1"
NS_BASE="${API}/namespaces/${ARETE_NAMESPACE}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAMPLE_SPEC="${SAMPLE_SPEC:-${HERE}/sample-openapi.yaml}"

# curl with the submitter cookie always attached, failing the script on a 5xx
# but still printing 4xx bodies (so problem+json is visible).
areq() {
  curl -sS --fail-with-body \
    -b "arete_submitter=${ARETE_SUBMITTER}" \
    "$@"
}

# Render a JSON response: pretty via jq if present, else raw.
show() {
  if command -v jq >/dev/null 2>&1; then jq .; else cat; fi
}

# Build the repeated ?run=<validator>/<policy> query string from ARETE_RUN.
run_query() {
  local q="" combo
  local IFS=','
  for combo in $ARETE_RUN; do
    combo="${combo#"${combo%%[![:space:]]*}"}"   # ltrim
    q+="${q:+&}run=$(urlencode "$combo")"
  done
  printf '%s' "$q"
}

urlencode() {
  local s="$1" out="" c i
  for (( i = 0; i < ${#s}; i++ )); do
    c="${s:i:1}"
    case "$c" in
      [a-zA-Z0-9._~-]) out+="$c" ;;
      *) printf -v c '%%%02X' "'$c"; out+="$c" ;;
    esac
  done
  printf '%s' "$out"
}

banner() { printf '\n\033[1m# %s\033[0m\n' "$*" >&2; }
