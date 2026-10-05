---
id: CASE001
category: Naming
matcher: naming
scope: property
parameters: { convention: camelCase, match: non-conforming }
---

# CASE001 — JSON property is not camelCase

## Intent

JSON property names should use camelCase where required by policy. Consistent
property naming reduces client mapping code and makes payloads easier to read.

## Detection and scope

The rule has `property` scope and uses the `naming` rule:

```yaml
parameters: { convention: camelCase, match: non-conforming }
```

It examines properties in normalised component schemas. A name conforms when
it matches `[a-z][A-Za-z0-9]*`: a lowercase initial letter followed by ASCII
letters or digits. Non-conforming properties are reported at their property
pointer with `Name does not use the configured convention`.

## Review-candidate example

```yaml
components:
  schemas:
    Customer:
      type: object
      properties:
        customer_id: { type: string }
```

`customerId` follows the configured convention.

## Compliant example

```yaml
properties:
  customerId: { type: string }
  email2: { type: string }
```

## Parameters, references, and limitations

The rule fixes camelCase and does not inspect path names, parameters, schema
names, JSON examples, serialised runtime payloads, or external naming
requirements. Underscores, hyphens, spaces, uppercase initials, and non-ASCII
characters do not conform. Referenced schemas count only after host
normalisation; exceptions may be needed for externally owned fields.
