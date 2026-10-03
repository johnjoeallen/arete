---
id: SECURITY003
category: Security
matcher: security-scheme
scope: api
parameters: { check: defined }
---

# SECURITY003 — Security requirement names an undefined scheme

## Intent

A `security` requirement references a security scheme by name. If that name is
not declared under `components.securitySchemes`, the requirement is invalid —
and most tooling responds by silently ignoring it. The operation then appears
secured in the source but is treated as public by generators, mock servers,
and gateways. A single typo (`bearer` for `bearerAuth`) is enough to leave an
endpoint unprotected with no visible error.

## Detection and scope

The rule has `api` scope and uses the `security-scheme` matcher with
`check: defined`. It walks every `security` requirement — the global list and
each operation's — and, for every scheme name in it, checks that a matching
entry exists in `components.securitySchemes`. Each unresolved name is reported
once, at the operation pointer (or `/security` for a global requirement).

## Diagnostic

```yaml
paths:
  /orders:
    get:
      security:
        - bearer: []            # scheme is 'bearerAuth', not 'bearer'
      responses: { '200': { description: OK } }
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
```

`GET /orders` is reported: it requires `bearer`, which is not defined.

## Compliant

```yaml
paths:
  /orders:
    get:
      security:
        - bearerAuth: []
      responses: { '200': { description: OK } }
components:
  securitySchemes:
    bearerAuth: { type: http, scheme: bearer }
```

## Configuration and limitations

`check: defined` is the rule's only mode. It matches scheme names literally
against the declared set and does not validate the scheme's type, flows, or
the scopes requested against those the scheme declares — `SECURITY002` covers
scope expectations. An empty `security: []` (explicitly public) is not a
finding.
