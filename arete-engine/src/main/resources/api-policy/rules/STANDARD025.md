---
id: STANDARD025
category: Standards
matcher: path-syntax
scope: api
parameters: { check: no-query }
---

# STANDARD025 — Path key contains a query string

## Intent

A key under `paths` is a URL template for the path portion only. A `?` in the
key means query parameters have been written into the path string, where the
OpenAPI tooling will not treat them as parameters: they are not validated, not
documented as inputs, and not offered by generated clients. Query parameters
belong in the operation's `parameters` list with `in: query`.

## Detection and scope

The rule has `api` scope and uses the `path-syntax` matcher with
`check: no-query`. Every key under `paths` that contains `?` is reported once,
at the path pointer.

## Diagnostic

```yaml
paths:
  /orders?status=open:
    get:
      responses: { '200': { description: OK } }
```

## Compliant

```yaml
paths:
  /orders:
    get:
      parameters:
        - name: status
          in: query
          schema: { type: string, enum: [open, closed] }
      responses: { '200': { description: OK } }
```

## Configuration and limitations

`check: no-query` is the mode used here; the matcher also offers
`check: no-fragment` for `#` in a path key, which no bundled rule enables. The
rule is a literal substring check and does not attempt to rewrite the path or
recover the intended parameters.
