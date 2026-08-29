---
id: JSON013
category: JSON
detector: schema
scope: property
parameters: { enum-type: consistent }
---

# JSON013 — Enum values do not match the property type

## Intent

Enum values should use the declared schema property type consistently. A
contract that declares a string property but supplies numeric enum values, for
example, can cause validation failures or incompatible generated clients.

## Detection and scope

The rule has `property` scope and uses the `schema` detector:

```yaml
parameters: { enum-type: consistent }
```

For each property with an enum, the detector compares values with the
declared primitive type. String properties require string values; integer
properties require integer values; number properties accept integer or floating
point values. If any enum value is inconsistent, the property is reported at
its pointer with `Property matches the configured schema rule`. Properties
without enums are not reported.

## Review-candidate example

This property declares `string` but contains a numeric enum value:

```yaml
components:
  schemas:
    Customer:
      type: object
      properties:
        segment:
          type: string
          enum: [1, '2']
```

## Compliant example

The values here agree with the declared type:

```yaml
components:
  schemas:
    Customer:
      type: object
      properties:
        score:
          type: number
          enum: [0, 0.5, 1]
```

## Parameters, references, and limitations

`enum-type: consistent` selects this check. The detector handles only string,
integer, and number primitive declarations; other or missing property types do
not produce a type inconsistency from this branch. It does not validate enum
uniqueness, bounds, nullable values, serialization quirks, runtime payloads,
or schema combinations. Referenced properties count only after host
normalisation, and findings are based on the parsed values rather than the
source text’s spelling.
