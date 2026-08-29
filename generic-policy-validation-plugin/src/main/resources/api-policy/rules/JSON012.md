---
id: JSON012
category: JSON
detector: schema
scope: property
parameters: { format: absent }
---

# JSON012 — Numeric property has no format

## Intent

Integer and number properties should declare an appropriate format when the
API’s tooling or consumers use format metadata to choose storage, validation,
or generated language types. The desired format is domain-specific, so this
rule is a policy review aid.

## Detection and scope

The rule has `property` scope and uses the `schema` detector:

```yaml
parameters: { format: absent }
```

In the current detector implementation, this branch excludes non-numeric
properties and reports numeric properties as matching the configured rule. It
does not currently test the property’s actual `format` value, so both numeric
properties with a format and numeric properties without one can be reported.
The occurrence points to the property with the generic schema-rule message.

## Review-candidate example

This numeric property is eligible for the current check:

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

Non-numeric properties are excluded by this detector branch:

```yaml
components:
  schemas:
    Measurement:
      type: object
      properties:
        label:
          type: string
```

Because the current implementation does not inspect the format value, adding
`format: float` to a numeric property does not guarantee that it avoids a
finding.

## Parameters, references, and limitations

`format: absent` is the configured parameter, but its present implementation
semantics are numeric-property selection rather than a true missing-format
test. The detector does not infer a suitable format, validate numeric ranges,
inspect runtime serialization, or distinguish integer and number formats
beyond their primitive types. Referenced properties count only when resolved
into the host’s normalised schema facts. This documentation records the
current behavior; tightening the detector to check actual format absence would
be a separate implementation change.
