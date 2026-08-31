# Automation API

A small JSON API for CI pipelines and scripts: submit an OpenAPI spec (inline
or by URL), name the validator/policy combinations to run it against, and get
the findings back with a pass/fail verdict — without opening the browser UI.

Everything lives under `/api/v1`. The browser UI is unchanged.

!!! danger "No authentication — put it behind a protected boundary"
    Areté performs **no authentication or authorization** on this API. The
    namespace and submitter are self-asserted labels, as forgeable as an HTTP
    header. The deployment **must** sit behind an authenticating reverse proxy,
    a private network, a VPN, or an internal-only ingress. Exposing `/api/v1`
    directly to an untrusted network is a misconfiguration, not a supported
    mode. See [Trust model](#trust-model).

## Concepts

| | |
|---|---|
| **Namespace** | Which pile a spec belongs to — a plain slug (`payments`, `mobile-ci`). Not a table, not isolation; the set of namespaces is just the distinct values in use. Spec titles are unique **per namespace**. |
| **Submitter** | Who submitted a spec — a slug (`payments-ci`). Recorded for attribution and filtering; never checked. **Required on every POST**, via the `arete_submitter` cookie or the `X-Arete-Submitter` header. |
| **Combination** | A `validator/policy` pair, e.g. `generic-policy/Enterprise Grade`. Every POST names at least one; the API never guesses. |
| **Level** | The bar a combination must clear: `blocker`, `error`, or `score<NN`. Each policy suggests its own (see [Scoring level](#scoring-level)); `?failOn` overrides. |

Both `namespace` and `submitter` slugs match `[a-z0-9][a-z0-9._-]{0,62}`,
lower-cased and trimmed.

## Submit and validate

```
POST /api/v1/namespaces/{namespace}/specs
```

One call parses, stores, and validates. The request body:

=== "Raw spec (YAML or OpenAPI JSON)"

    ```bash
    curl -X POST 'http://arete.internal/api/v1/namespaces/payments/specs?run=generic-policy/Enterprise%20Grade' \
      -b 'arete_submitter=payments-ci' \
      -H 'Content-Type: application/yaml' \
      --data-binary @openapi.yaml
    ```

    `Content-Type`: `application/yaml`, `text/yaml`, `application/x-yaml`,
    `text/plain`, or `application/json` when the body has a top-level
    `openapi`/`swagger` key.

=== "By URL"

    ```bash
    curl -X POST 'http://arete.internal/api/v1/namespaces/payments/specs' \
      -b 'arete_submitter=payments-ci' \
      -H 'Content-Type: application/json' \
      -d '{ "url": "https://git.internal/payments/-/raw/main/openapi.yaml",
            "run": [ { "validator": "generic-policy", "policy": "Enterprise Grade" } ] }'
    ```

    Areté fetches the URL server-side — `http`/`https` only, SSRF-guarded (see
    [Remote fetch](#remote-fetch)).

**Combinations** are given as repeatable `?run=<validator>/<policy>` query
params (any body style) or a `run` array in a JSON body. A POST with none is
`400`.

**Response** (`201` if the spec is new, else `200`):

```json
{
  "spec": {
    "id": 42, "namespace": "payments", "title": "Payments API",
    "submitter": "payments-ci", "source": "URL",
    "sourceUrl": "https://git.internal/.../openapi.yaml",
    "updatedAt": "2026-09-01T10:00:00Z",
    "links": { "self": "/api/v1/namespaces/payments/specs/42", "ui": "/spec/42" }
  },
  "ok": false,
  "verdict": "FAIL",
  "results": [
    {
      "validator": "generic-policy", "policy": "Enterprise Grade",
      "status": "SUCCESS", "score": 87.5,
      "level": { "criterion": "score<90", "source": "policy", "met": false },
      "counts": { "error": 0, "warning": 5, "info": 1, "hint": 0 },
      "rulesEvaluated": 109,
      "findings": [
        { "ruleId": "DOC001", "severity": "WARNING", "title": "…", "message": "…",
          "pointer": "/paths/~1orders/post", "paths": ["POST /orders"], "documentationUrl": "…" }
      ]
    }
  ]
}
```

`ok` / `verdict` are `FAIL` if **any** combination fails its level. Re-validating
is just re-POSTing the spec (upsert by `(namespace, title)`).

### Response shaping

| query param | effect |
|---|---|
| `?failOn=` | `policy` (default — each combination uses its policy's suggested level), or `never` / `error` / `blocker` / `score<NN` to force one criterion across all. |
| `?format=sarif` (or `Accept: application/sarif+json`) | Return SARIF 2.1.0 instead of the JSON above — for GitHub code scanning and other consumers. |
| `?httpStatusOnFail=422` | Return `422` instead of `200`/`201` on a failing verdict, for clients that branch on status. |

## Read and manage

```
GET    /api/v1/namespaces                              → [{ "slug": "...", "specCount": N }]
GET    /api/v1/namespaces/{namespace}/specs            → spec summaries; ?submitter= to filter
GET    /api/v1/namespaces/{namespace}/specs/{id}       → one spec resource
GET    /api/v1/namespaces/{namespace}/specs/{id}/validation   → last validation result; ?format=sarif
DELETE /api/v1/namespaces/{namespace}/specs/{id}       → 204
```

Errors are `application/problem+json` (`{ status, title, detail }`).

## Scoring level

A policy declares its own suggested gate in a `scoring:` key in its front
matter — `blocker`, `error`, or `score<NN`. The bundled policies:

| policy | suggested gate |
|---|---|
| Enterprise Grade | `score<90` |
| Zalando, Zalando Extended | `error` |

With the default `?failOn=policy`, each combination is judged against its own
policy's gate (`level.source` = `"policy"`). A policy with no `scoring:` key
falls back to `blocker`. An explicit `?failOn=error` etc. overrides every
combination (`level.source` = `"request"`).

Plugins other than the policy engine can expose a suggestion too, via the SPI
method `getSuggestedScoreLevel(ruleSet)`.

## Remote fetch

`{ "url": … }` is fetched server-side with these constraints:

- `http` / `https` only — `file:` and everything else are rejected `422`.
- The host is resolved and **blocked** if it is loopback, any-local,
  link-local, site-local (RFC 1918), multicast, IPv6 unique-local, or
  `169.254.169.254`. The check re-runs on every redirect (limit 5).
- Timeout `arete.api.url-fetch.timeout` (default `10s`); size cap
  `arete.openapi.max-document-size` (default `50MB`).
- `arete.api.url-fetch.allow-private=true` disables the address guard — for a
  single-host or dev deployment. **Ignored in `shared` deployment mode.**

## Deployment mode

`arete.deployment.mode` — `local` (default) or `shared`.

| | `local` | `shared` |
|---|---|---|
| `/api/load-file` (read a local path) | works | `403` |
| drop-folder watcher (`~/.arete/specs`) | on | off |
| `file:` URLs | n/a (API is http/https only) | rejected explicitly |
| `arete.api.url-fetch.allow-private` | honoured | ignored |

Set `shared` for any multi-user or network-exposed instance.

## Trust model

- **No authentication.** On a trusted network a bare `curl` is the point.
- **The API must sit behind a protected boundary** — an authenticating proxy,
  private network, VPN, or internal ingress. This is a requirement, not a
  recommendation.
- Past that boundary, namespace and submitter are organisational only — anyone
  who can reach the server can read and write any namespace.
- The remote fetcher is the one surface that survives the boundary (an authed
  caller pointing it inward); its SSRF guard is mandatory and cannot be
  disabled in `shared` mode.

## CI example (GitHub Actions)

```yaml
- name: API compliance gate
  run: |
    curl -sS --fail-with-body \
      -X POST "$ARETE_URL/api/v1/namespaces/payments/specs?run=generic-policy/Enterprise%20Grade&httpStatusOnFail=422" \
      -b "arete_submitter=gha-${{ github.repository }}" \
      -H 'Content-Type: application/yaml' \
      --data-binary @openapi.yaml | tee arete-result.json
  env:
    ARETE_URL: https://arete.internal.example.com
```

A non-2xx exit fails the step; `arete-result.json` has the findings. Ready-made
scripts (including SARIF upload) are in
[`scripts/automation-api/`](https://github.com/johnjoeallen/arete/tree/main/scripts/automation-api).
