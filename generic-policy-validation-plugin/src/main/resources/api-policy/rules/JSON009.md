---
id: JSON009
category: JSON
detector: schema
scope: property
parameters: { type: integer, enum: present }
---

# JSON009 — Numeric enum is used

## Intent

The API represents a finite set of semantic values using a numeric enum. String
symbols are often easier to read in logs, safer to extend, and less likely to
be confused when numeric meanings change. Numeric enums can nevertheless be
appropriate for established wire contracts, so this rule produces a review
candidate.

## Detection and scope

The rule has `property` scope and uses the `schema` detector:

```yaml
parameters: { type: integer, enum: present }
```

It reports every schema property whose declared type is exactly `integer` and
whose enum is present. The occurrence points to the property and uses the
generic schema-rule message `Property uses an enum`. The detector does not
inspect the individual values beyond the presence of the enum declaration.

## Review-candidate example

This numeric enum is reported:

```yaml
components:
  schemas:
    Order:
      type: object
      properties:
        status:
          type: integer
          enum: [1, 2, 3]
```

If the values represent semantic states, symbolic values such as `PENDING`,
`PAID`, and `CANCELLED` may be clearer.

## Compliant example

This string enum does not match because its property type is not `integer`:

```yaml
properties:
  status:
    type: string
    enum: [PENDING, PAID, CANCELLED]
```

## Parameters, references, and limitations

The rule requires both `type: integer` and `enum: present`; number, string, and
array enums are outside this rule. It does not validate enum value ranges,
uniqueness, semantic meaning, serialization, compatibility, or runtime JSON.
Referenced properties count only when resolved by the host. Findings are a
policy prompt rather than an assertion that every numeric enum should change.
