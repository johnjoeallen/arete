---
id: STANDARD018
category: Standards
matcher: extensions
scope: api
parameters: { allowed: "x-api-id,x-audience,x-extensible-enum" }
---

# STANDARD018 — Non-standard specification extension

## Intent

Vendor `x-` extensions couple the contract to a specific toolchain. A policy
should allow-list the extensions it recognises and flag the rest.

## Diagnostic

```yaml
paths:
  /customers:
    get:
      x-internal-cache-ttl: 30
      responses: { '200': { description: OK } }
```

## Compliant

```yaml
info:
  x-api-id: 11111111-2222-3333-4444-555555555555
  x-audience: external-public
```

## Detection and scope

The rule has `api` scope and uses the `extensions` rule. It scans the
`x-` keys on `info`, every operation, every component schema and its
properties, and every parameter, reporting each key not present in the
comma-separated `allowed` list.

## Configuration and limitations

`allowed` is a policy parameter. The rule inspects only the extension
locations exposed by the stable model; extensions on response objects,
servers, or security schemes are not currently visible.
