---
id: STANDARD017
category: Standards
matcher: header-schema
scope: response
parameters: {}
---

# STANDARD017 — Response header has no schema

A documented response header should declare its type through a `schema` (or a
`content` object). An untyped header cannot be validated or represented in a
generated client.

## Diagnostic

```yaml
responses:
  '200':
    description: OK
    headers:
      X-Rate-Limit:
        description: Requests remaining
```

## Compliant

```yaml
responses:
  '200':
    description: OK
    headers:
      X-Rate-Limit:
        description: Requests remaining
        schema: { type: integer }
```

## Detection and scope

The rule has `response` scope and uses the `header-schema` rule. Every
header on every documented response is checked; one diagnostic is reported per
header that declares neither `schema` nor `content`.

## Configuration and limitations

The rule checks presence, not correctness. Headers defined only on a
referenced component are checked after host normalisation.
