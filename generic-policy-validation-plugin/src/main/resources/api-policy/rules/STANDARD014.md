---
id: STANDARD014
category: Standards
detector: parameter
scope: parameter
parameters: { check: schema-present }
---

# STANDARD014 — Parameter has no schema or content

Every parameter must define its type through either a `schema` or a `content`
object. A parameter with neither is untyped and cannot be validated or used to
generate a client.

## Violation

```yaml
parameters:
  - name: filter
    in: query
```

## Compliant

```yaml
parameters:
  - name: filter
    in: query
    schema: { type: string }
```

## Detection and scope

The rule has `parameter` scope and uses the `parameter` detector with
`check: schema-present`. Path-level and operation-level parameters are checked;
one occurrence is reported per parameter that declares neither `schema` nor
`content`.

## Configuration and limitations

The detector checks for the presence of a schema or content object, not its
correctness. A parameter whose schema is an unresolved `$ref` counts as
present.
