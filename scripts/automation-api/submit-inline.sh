#!/usr/bin/env bash
# Submit a spec as a raw YAML body and run the configured validator/policy
# combinations. Usage: submit-inline.sh [path/to/spec.yaml]
source "$(dirname "$0")/_common.sh"

SPEC="${1:-$SAMPLE_SPEC}"

banner "POST ${NS_BASE}/specs  (inline YAML: ${SPEC})"
areq -X POST "${NS_BASE}/specs?$(run_query)" \
  -H 'Content-Type: application/yaml' \
  --data-binary "@${SPEC}" | show
