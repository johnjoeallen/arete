# Automation API — test scripts

`curl` scripts for exercising the automation API described in
[`design-notes/automation-api.md`](../../design-notes/automation-api.md).

> **Status:** the API is not implemented yet. These scripts double as an
> executable spec of the planned surface; run them against a build once the
> `/api/v1` endpoints land.

## Configuration

Every script sources `_common.sh`, which reads these from the environment:

| var | default | meaning |
|---|---|---|
| `ARETE_URL` | `http://localhost:6809` | base URL |
| `ARETE_NAMESPACE` | `default` | namespace slug |
| `ARETE_SUBMITTER` | `curl-test` | submitter label, sent as the `arete_submitter` cookie |
| `ARETE_RUN` | `generic-policy/Enterprise Grade` | comma-separated `<validator>/<policy>` combinations |
| `ARETE_FAIL_ON` | `policy` | `policy` \| `never` \| `error` \| `blocker` \| `score<NN` |

`jq`, if installed, is used to pretty-print responses.

```bash
export ARETE_NAMESPACE=payments
export ARETE_SUBMITTER=payments-ci
export ARETE_RUN='generic-policy/Enterprise Grade,generic-policy/Zalando'
```

## Scripts

| script | what it does |
|---|---|
| `submit-inline.sh [spec.yaml]` | POST a raw YAML spec + run the combinations |
| `submit-url.sh <url>` | POST `{ url, run }` — server fetches the spec |
| `sarif.sh [spec.yaml]` | submit, get the result as SARIF 2.1.0 |
| `ci-gate.sh [spec.yaml]` | submit with `failOn` + `httpStatusOnFail=422`; exit non-zero on FAIL |
| `list.sh [namespaces \| specs [submitter]]` | list namespaces, or specs in the current one |
| `spec.sh <id> [validation [sarif] \| delete]` | read or delete one stored spec |
| `no-submitter.sh` | negative test — POST without a submitter must be `400` |
| `smoke.sh` | full walk-through in a throwaway namespace |

`sample-openapi.yaml` is the default fixture; pass a path to any script that
takes one to use your own.

## CI example (GitHub Actions)

```yaml
- name: API compliance gate
  env:
    ARETE_URL: https://arete.internal.example.com
    ARETE_NAMESPACE: payments
    ARETE_SUBMITTER: gha-${{ github.repository }}
    ARETE_FAIL_ON: policy
  run: scripts/automation-api/ci-gate.sh openapi.yaml
```
