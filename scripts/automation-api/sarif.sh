#!/usr/bin/env bash
# Submit a spec and get the result as SARIF 2.1.0 (for GitHub code scanning).
# Usage: sarif.sh [path/to/spec.yaml] > results.sarif
source "$(dirname "$0")/_common.sh"

SPEC="${1:-$SAMPLE_SPEC}"

banner "POST ${NS_BASE}/specs?format=sarif  (${SPEC})"
areq -X POST "${NS_BASE}/specs?$(run_query)&format=sarif" \
  -H 'Content-Type: application/yaml' \
  -H 'Accept: application/sarif+json' \
  --data-binary "@${SPEC}" | show
