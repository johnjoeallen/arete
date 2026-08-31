#!/usr/bin/env bash
# Read or delete a single stored spec by id.
# Usage:
#   spec.sh <id>              → the spec resource
#   spec.sh <id> validation   → its last validation result
#   spec.sh <id> validation sarif → …as SARIF
#   spec.sh <id> delete       → delete it
source "$(dirname "$0")/_common.sh"

ID="${1:?usage: spec.sh <id> [validation [sarif] | delete]}"
ACTION="${2:-get}"

case "$ACTION" in
  get)
    banner "GET ${NS_BASE}/specs/${ID}"
    areq "${NS_BASE}/specs/${ID}" | show ;;
  validation)
    q=""
    [ "${3:-}" = "sarif" ] && q="?format=sarif"
    banner "GET ${NS_BASE}/specs/${ID}/validation${q}"
    areq "${NS_BASE}/specs/${ID}/validation${q}" | show ;;
  delete)
    banner "DELETE ${NS_BASE}/specs/${ID}"
    areq -X DELETE "${NS_BASE}/specs/${ID}" -o /dev/null -w 'HTTP %{http_code}\n' ;;
  *)
    echo "usage: spec.sh <id> [validation [sarif] | delete]" >&2; exit 64 ;;
esac
