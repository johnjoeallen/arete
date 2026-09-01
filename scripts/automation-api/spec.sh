#!/usr/bin/env bash
# Read or delete a single stored spec by id.
# Usage:
#   spec.sh <id>              → the spec resource
#   spec.sh <id> scoring   → its last scoring result
#   spec.sh <id> scoring sarif → …as SARIF
#   spec.sh <id> delete       → delete it
source "$(dirname "$0")/_common.sh"

ID="${1:?usage: spec.sh <id> [scoring [sarif] | delete]}"
ACTION="${2:-get}"

case "$ACTION" in
  get)
    banner "GET ${NS_BASE}/specs/${ID}"
    areq "${NS_BASE}/specs/${ID}" | show ;;
  scoring)
    q=""
    [ "${3:-}" = "sarif" ] && q="?format=sarif"
    banner "GET ${NS_BASE}/specs/${ID}/scoring${q}"
    areq "${NS_BASE}/specs/${ID}/scoring${q}" | show ;;
  delete)
    banner "DELETE ${NS_BASE}/specs/${ID}"
    areq -X DELETE "${NS_BASE}/specs/${ID}" -o /dev/null -w 'HTTP %{http_code}\n' ;;
  *)
    echo "usage: spec.sh <id> [scoring [sarif] | delete]" >&2; exit 64 ;;
esac
