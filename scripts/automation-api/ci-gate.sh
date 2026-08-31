#!/usr/bin/env bash
# The CI pattern: submit a spec, apply ARETE_FAIL_ON, and exit non-zero if the
# verdict is FAIL. Prints a one-line summary plus the findings.
# Usage: ci-gate.sh [path/to/spec.yaml]
source "$(dirname "$0")/_common.sh"

SPEC="${1:-$SAMPLE_SPEC}"

banner "CI gate: ${SPEC}  (failOn=${ARETE_FAIL_ON}, run=${ARETE_RUN})"

# ?httpStatusOnFail=422 lets us branch on curl's exit code instead of parsing.
http_code=$(curl -sS -o /tmp/arete-ci-gate.$$ -w '%{http_code}' \
  -b "arete_submitter=${ARETE_SUBMITTER}" \
  -X POST "${NS_BASE}/specs?$(run_query)&failOn=$(urlencode "$ARETE_FAIL_ON")&httpStatusOnFail=422" \
  -H 'Content-Type: application/yaml' \
  --data-binary "@${SPEC}")
body=$(cat /tmp/arete-ci-gate.$$); rm -f /tmp/arete-ci-gate.$$

echo "$body" | show

if command -v jq >/dev/null 2>&1; then
  jq -r '"\nverdict=\(.verdict)  " +
    ( [ .results[] | "\(.validator)/\(.policy): score=\(.score) level=\(.level.criterion)(\(.level.source)) met=\(.level.met)" ] | join("\n") )' <<<"$body" >&2
fi

case "$http_code" in
  2*) echo "PASS" >&2; exit 0 ;;
  422) echo "FAIL (verdict)" >&2; exit 1 ;;
  *)  echo "ERROR (HTTP $http_code)" >&2; exit 2 ;;
esac
