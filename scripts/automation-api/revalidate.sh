#!/usr/bin/env bash
# Re-score an already-submitted spec by its UUID (submit -> keep uuid -> score
# later). Usage: revalidate.sh <spec-uuid>
source "$(dirname "$0")/_common.sh"

UUID="${1:?usage: revalidate.sh <spec-uuid>}"

banner "POST ${API}/specs/${UUID}/validate  (run=${ARETE_RUN}, failOn=${ARETE_FAIL_ON})"
areq -X POST "${API}/specs/${UUID}/validate?$(run_query)&failOn=$(urlencode "$ARETE_FAIL_ON")" | show
