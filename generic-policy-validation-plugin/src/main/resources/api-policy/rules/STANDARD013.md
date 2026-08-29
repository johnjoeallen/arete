---
id: STANDARD013
category: Standards
detector: parameter
scope: parameter
parameters: { check: template-match }
---

# STANDARD013 — Path parameter does not match the path template

Every `{placeholder}` in a path template must have a matching path parameter,
and every declared path parameter must correspond to a placeholder. A mismatch
means the operation cannot be routed as written.

## Violation

```yaml
/customers/{customerId}:
  get:
    parameters:
      - { name: id, in: path, required: true, schema: { type: string } }
```

## Compliant

```yaml
/customers/{customerId}:
  get:
    parameters:
      - { name: customerId, in: path, required: true, schema: { type: string } }
```

## Detection and scope

The rule has `parameter` scope and uses the `parameter` detector with
`check: template-match`. For each operation the detector compares the set of
`{...}` tokens in the path against the names of parameters whose `in` is
`path`, reporting each unmatched name in either direction.

## Configuration and limitations

The detector compares names only. It does not validate the parameter schema or
detect duplicate placeholders within a single template.
