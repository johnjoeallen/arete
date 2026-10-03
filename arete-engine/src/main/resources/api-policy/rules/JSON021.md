---
id: JSON021
category: JSON
matcher: schema
scope: property
parameters: { bounds: complete }
---

# JSON021 — Numeric property has no minimum and maximum

## Intent

Every `integer` or `number` property should declare both a `minimum` and a
`maximum`. Explicit bounds document the valid range, let clients validate
input early, and stop an unbounded value from reaching storage or arithmetic
where it can overflow or exhaust resources.

## Detection and scope

The rule has `property` scope and uses the `schema` matcher with
`bounds: complete`. For every component-schema property whose `type` is
`integer` or `number`, the rule checks that both `minimum` and `maximum` are
present. A property missing either bound is reported once at its property
pointer.

## Review-candidate example

`quantity` is reported because it has no bounds:

```yaml
components:
  schemas:
    OrderLine:
      type: object
      properties:
        quantity: { type: integer }
```

## Compliant example

```yaml
components:
  schemas:
    OrderLine:
      type: object
      properties:
        quantity: { type: integer, minimum: 1, maximum: 999 }
```

## Parameters, references, and limitations

`bounds: complete` is the rule's only mode. It reads `minimum` and `maximum`
from the normalised model and does not consider `exclusiveMinimum`,
`exclusiveMaximum`, `multipleOf`, `format`, or bounds inherited through
composition. Non-numeric properties are ignored.
