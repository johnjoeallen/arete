---
id: JSON007
category: JSON
matcher: schema
scope: property
parameters: { type: string, enum: present }
---

# JSON007 — Closed string enum is used

## Intent

The API models a finite set of values using a closed string enum. Closed enums
are useful when the set is genuinely finite, but they can make adding a server
value a breaking change for strict clients. This is a policy choice and a
finding is a compatibility review candidate.

## Detection and scope

The rule has `property` scope and uses the `schema` rule:

```yaml
parameters: { type: string, enum: present }
```

It reports each schema property whose declared type is exactly `string` and
that declares an enum. Findings point to the property and use the generic
message `Property uses an enum`. The rule does not distinguish extensible
from closed enums in this rule.

## Review-candidate example

This string enum is reported:

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

Review whether future tiers could be added and whether the property should use
an extensibility convention such as JSON014.

## Compliant example

This free-form string has no enum and does not match:

```yaml
properties:
  tier:
    type: string
```

An integer enum is also outside JSON007 and is covered by JSON009’s configured
type check.

## Parameters, references, and limitations

The rule requires `type: string` and `enum: present`. It does not validate the
values, require UPPER_SNAKE_CASE, inspect `x-extensible-enum`, determine
whether the domain is truly finite, or observe runtime values. Referenced
properties count only after host normalisation. A finding does not require
removing the enum; it asks reviewers to assess compatibility and evolution.
