#!/usr/bin/env bash
# End-to-end walk-through: submit, list, read back, re-score, delete.
# Uses a throwaway namespace so it doesn't disturb real data.
source "$(dirname "$0")/_common.sh"

ARETE_NAMESPACE="smoke-$(date +%s)"
NS_BASE="${API}/namespaces/${ARETE_NAMESPACE}"
export ARETE_NAMESPACE

banner "smoke test in namespace '${ARETE_NAMESPACE}' as '${ARETE_SUBMITTER}'"

banner "1. submit (inline)"
resp=$(areq -X POST "${NS_BASE}/specs?$(run_query)" \
  -H 'Content-Type: application/yaml' --data-binary "@${SAMPLE_SPEC}")
echo "$resp" | show
id=$(command -v jq >/dev/null 2>&1 && jq -r '.spec.id' <<<"$resp" || echo "?")

banner "2. namespaces (should include '${ARETE_NAMESPACE}')"
areq "${API}/namespaces" | show

banner "3. specs in namespace"
areq "${NS_BASE}/specs" | show

banner "4. read spec ${id}"
areq "${NS_BASE}/specs/${id}" | show

banner "5. last scoring for spec ${id}"
areq "${NS_BASE}/specs/${id}/scoring" | show

banner "6. re-score (re-POST the same spec)"
areq -X POST "${NS_BASE}/specs?$(run_query)" \
  -H 'Content-Type: application/yaml' --data-binary "@${SAMPLE_SPEC}" | show

banner "7. delete spec ${id}"
areq -X DELETE "${NS_BASE}/specs/${id}" -o /dev/null -w 'HTTP %{http_code}\n'

banner "done"
