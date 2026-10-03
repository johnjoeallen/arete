---
id: JSON004
category: JSON
matcher: naming
scope: property
parameters: { schema-type: array, semantic: singular }
---

# JSON004 — Array property has a singular name

## Intent

A property containing a collection should normally use a plural name so that
JSON readers can tell a list from a single value. This is an English naming
convention and is not reliable for every language or domain term.

## Detection and scope

The rule has `property` scope and uses the `naming` rule:

```yaml
parameters: { schema-type: array, semantic: singular }
```

It examines array schema properties and reports a property when its name does
not end in `s` (case-insensitive, with names longer than one character). The
finding points to the property and says `Array property has a singular name`.

## Review-candidate example

The singular `customer` property is reported:

```yaml
components:
  schemas:
    Account:
      type: object
      properties:
        customer:
          type: array
          items: { type: string }
```

If the value is a collection, `customers` may be clearer.

## Compliant example

This plural-looking array property does not match:

```yaml
properties:
  customers:
    type: array
    items: { type: string }
```

## Parameters, references, and limitations

Both configured parameters are required for the intended check. The rule
uses only the property’s normalised name and top-level type; it does not
inspect item schemas, values, descriptions, or runtime JSON. Irregular plurals
such as `people` may be flagged, while a word ending in `s` may be a singular
domain term. Referenced properties count only after host normalisation.
