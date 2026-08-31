#!/usr/bin/env bash
# Negative test: a POST with no submitter cookie / header must be rejected 400.
source "$(dirname "$0")/_common.sh"

banner "POST ${NS_BASE}/specs with NO submitter (expect 400)"
curl -sS -o /tmp/arete-no-sub.$$ -w 'HTTP %{http_code}\n' \
  -X POST "${NS_BASE}/specs?$(run_query)" \
  -H 'Content-Type: application/yaml' \
  --data-binary "@${SAMPLE_SPEC}"
cat /tmp/arete-no-sub.$$ | show; rm -f /tmp/arete-no-sub.$$
