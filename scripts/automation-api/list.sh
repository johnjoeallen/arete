#!/usr/bin/env bash
# List namespaces, or the specs in the current namespace.
# Usage:
#   list.sh                 → all namespaces + spec counts
#   list.sh specs           → specs in $ARETE_NAMESPACE
#   list.sh specs <submitter> → …filtered by submitter
source "$(dirname "$0")/_common.sh"

case "${1:-namespaces}" in
  namespaces)
    banner "GET ${API}/namespaces"
    areq "${API}/namespaces" | show ;;
  specs)
    q=""
    [ -n "${2:-}" ] && q="?submitter=$(urlencode "$2")"
    banner "GET ${NS_BASE}/specs${q}"
    areq "${NS_BASE}/specs${q}" | show ;;
  *)
    echo "usage: list.sh [namespaces | specs [submitter]]" >&2; exit 64 ;;
esac
