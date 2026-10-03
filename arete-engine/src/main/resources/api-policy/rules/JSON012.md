---
id: JSON012
category: JSON
matcher: schema
scope: property
parameters: { format: absent }
---

# JSON012 — Numeric property has no format

## Intent

Integer and number properties should declare an appropriate format when the
API’s tooling or consumers use format metadata to choose storage, scoring,
or generated language types. The desired format is domain-specific, so this
rule is a policy review aid.

## Detection and scope

The rule has `property` scope and uses the `schema` rule:

```yaml
parameters: { format: absent }
```

The rule selects integer and number properties whose `format` is absent.
The diagnostic points to the property with the generic schema-rule message.

## Review-candidate example

This numeric property is eligible for the check:

```yaml
components:
  schemas:
    Measurement:
      type: object
      properties:
        amount:
          type: number
```

The appropriate format—if one exists for the domain and target tooling—should
be selected deliberately.

## Compliant example

Numeric properties with a format, and non-numeric properties, are excluded:

```yaml
components:
  schemas:
    Measurement:
      type: object
      properties:
        amount:
          type: number
          format: double
        label:
          type: string
```

`format` values are treated as present when they are non-empty; the rule does
not prescribe which format is appropriate for a particular domain.

## Parameters, references, and limitations

`format: absent` is the configured parameter. The rule does not infer a
suitable format, validate numeric ranges, inspect runtime serialization, or
distinguish integer and number formats beyond their primitive types. Referenced
properties count only when resolved into the host’s normalised schema facts.
