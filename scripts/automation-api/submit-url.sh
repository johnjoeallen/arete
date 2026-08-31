#!/usr/bin/env bash
# Submit a spec by URL (the server fetches it) with a JSON body carrying the
# run combinations. Usage: submit-url.sh <https://…/openapi.yaml>
source "$(dirname "$0")/_common.sh"

URL="${1:?usage: submit-url.sh <spec-url>}"

# Build the run array from ARETE_RUN.
run_json() {
  local first=1 combo
  local IFS=','
  printf '['
  for combo in $ARETE_RUN; do
    combo="${combo#"${combo%%[![:space:]]*}"}"
    local validator="${combo%%/*}" policy="${combo#*/}"
    [ $first -eq 1 ] || printf ','
    first=0
    printf '{"validator":"%s","policy":"%s"}' "$validator" "$policy"
  done
  printf ']'
}

banner "POST ${NS_BASE}/specs  (by URL: ${URL})"
areq -X POST "${NS_BASE}/specs" \
  -H 'Content-Type: application/json' \
  --data "$(printf '{"url":"%s","run":%s}' "$URL" "$(run_json)")" | show
