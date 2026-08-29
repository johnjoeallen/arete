---
id: JSON014
category: JSON
detector: schema
scope: property
parameters: { extensible: required }
---

# JSON014 — Closed enum should be extensible

## Intent

Enumerations should permit future values when clients can safely tolerate
unknown members. An extensible enum communicates that the listed values are
known examples rather than a permanently closed set. This policy is not right
for every finite domain, especially when unknown values would be unsafe.

## Detection and scope

The rule has `property` scope and uses the `schema` detector:

```yaml
parameters: { extensible: required }
```

For each property that declares an enum, the detector reports it when the
normalised property does not have `x-extensible-enum` enabled. The occurrence
points to the property and says `Property matches the configured schema rule`.
Properties without an enum are not reported.

## Review-candidate example

This closed enum is reported:

```yaml
components:
  schemas:
    Customer:
      type: object
      properties:
        tier:
          type: string
          enum: [STANDARD, PREMIUM]
```

## Compliant example

This enum explicitly opts into extension:

```yaml
components:
  schemas:
    Customer:
      type: object
      properties:
        tier:
          type: string
          enum: [STANDARD, PREMIUM]
          x-extensible-enum: true
```

## Parameters, references, and limitations

`extensible: required` selects the check. The detector checks only the
presence/truth of the host’s normalised extensible-enum fact; it does not
validate the extension’s value semantics, enum types, descriptions, or client
behavior. It does not report properties without an enum. Referenced schemas
are considered only when their properties are resolved by the host, and
vendor-extension support may vary by OpenAPI tooling. Treat the result as a
compatibility review prompt.
