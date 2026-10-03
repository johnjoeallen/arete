---
id: STANDARD016
category: Standards
matcher: path-set
scope: api
parameters: { check: unique }
---

# STANDARD016 — Duplicate path structure

## Intent

Two paths that differ only in the name of a template parameter resolve to the
same route. OpenAPI tooling rejects or silently drops one of them.

## Diagnostic

```yaml
paths:
  /pets/{id}: {}
  /pets/{petId}: {}
```

## Compliant

```yaml
paths:
  /pets/{petId}: {}
  /pets/{petId}/owner: {}
```

## Detection and scope

The rule has `api` scope and uses the `path-set` rule with `check: unique`.
Each path is normalised by replacing every `{...}` segment with `{}`; the
second and later paths that share a normalised form are reported.

## Configuration and limitations

Normalisation is purely textual. Paths that differ only by a trailing slash
are handled by a separate rule.
