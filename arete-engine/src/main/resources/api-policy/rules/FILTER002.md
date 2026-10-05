---
id: FILTER002
category: Collection capabilities
matcher: collection-capability
scope: query-parameter
parameters: { name-pattern: "(^|[-_])filter([-_]|$)", check: string }
---

# FILTER002 — Filter parameter is not a string expression

## Intent

Filter expressions should use a string representation unless a policy
explicitly defines another encoding.

## Review-candidate example

```yaml
name: filter
in: query
schema: { type: array }
```

## Compliant example

```yaml
name: filter
in: query
schema: { type: string }
```

## Detection and scope

The rule has `query-parameter` scope and reports parameters whose names match
the configured filter pattern when their schema type is not `string`.

## Configuration and limitations

`check: string` selects the representation check. The rule does not define the
filter expression grammar or validate server-side parsing.
