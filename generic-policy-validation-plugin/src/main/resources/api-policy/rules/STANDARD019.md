---
id: STANDARD019
category: Standards
detector: schema-composition
scope: schema
parameters: { check: inline-composition }
---

# STANDARD019 — Inline composition member

`allOf` / `anyOf` / `oneOf` members should reference reusable component
schemas rather than declare a schema inline, so the composed parts stay
named and reusable.

## Violation

```yaml
components:
  schemas:
    Customer:
      allOf:
        - $ref: '#/components/schemas/Party'
        - type: object
          properties:
            loyaltyTier: { type: string }
```

## Compliant

```yaml
components:
  schemas:
    Customer:
      allOf:
        - $ref: '#/components/schemas/Party'
        - $ref: '#/components/schemas/LoyaltyFields'
```

## Detection and scope

The rule has `schema` scope and uses the `schema-composition` detector with
`check: inline-composition`. A component schema that composes with `allOf`,
`anyOf`, or `oneOf` and has at least one member without a `$ref` is reported
once.

## Configuration and limitations

The detector counts inline members; it does not report which one. Composition
inside request or response body schemas is covered by STANDARD020.
