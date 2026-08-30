---
id: BULK002
category: Bulk operations
matcher: schema
scope: property
parameters:
  type: array
  max-items: absent
---

# BULK002 — Bulk request array has no maximum size

Bulk request arrays should normally place an explicit bound on the number of items accepted in one request.

## Detection and scope

The rule has `property` scope and uses the `schema` rule:

```yaml
parameters:
  type: array
  max-items: absent
```

The rule selects properties whose normalised type is `array` and then
requires that their `maxItems` fact is absent. Properties without a maximum
are reported with the generic schema-rule message. In the current rule,
the `type: array` parameter is applied before the maximum-items check, so
non-array properties are excluded.

## Review-candidate example

```yaml
components:
  schemas:
    BulkRequest:
      type: object
      properties:
        customers:
          type: array
          items: { $ref: '#/components/schemas/Customer' }
```

Choose a service-appropriate bound and document what happens when it is
exceeded.

## Compliant example

```yaml
properties:
  customers:
    type: array
    maxItems: 100
    items: { $ref: '#/components/schemas/Customer' }
```

## Parameters, references, and limitations

`type: array` and `max-items: absent` select the check. The rule checks the
declared schema facts only; it does not infer request size, inspect runtime
payloads, validate the chosen limit, or determine whether an array is used for
bulk mutation. Referenced properties count only after host normalisation.
Missing `maxItems` is a policy candidate, not proof that a server accepts an
unbounded request.
