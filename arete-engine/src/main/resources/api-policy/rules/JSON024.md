---
id: JSON024
category: JSON
matcher: schema
scope: property
parameters: {}
---

# JSON024 — Object schema should define additional-property semantics

## Intent

Object schemas should make their additional-property policy explicit. Leaving
the behaviour implicit can cause generators, validators, and clients to make
different assumptions about fields that are not listed in `properties`.

## Detection and scope

This catalogue document describes a future policy rule. JSON024 is not yet
listed in `PolicyBundle.yaml` and is therefore not evaluated by the current
runtime.

## Review-candidate example

```yaml
type: object
properties:
  name:
    type: string
```

The schema does not state whether additional properties are accepted.

## Compliant example

```yaml
type: object
additionalProperties: false
properties:
  name:
    type: string
```

## Configuration and limitations

No runtime matcher currently implements this rule. The examples document the
intended policy boundary for a future implementation.
