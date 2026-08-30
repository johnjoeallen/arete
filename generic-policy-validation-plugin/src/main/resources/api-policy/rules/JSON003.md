---
id: JSON003
category: JSON
matcher: naming
scope: property
parameters: { match: unsupported-character }
---

# JSON003 — Property name contains unsupported characters

## Intent

Property names should use a conservative JSON naming grammar so that generated
clients, serializers, and downstream tooling can handle them consistently.

## Detection and scope

The rule has `property` scope and uses the `naming` rule:

```yaml
parameters: { match: unsupported-character }
```

It reports a property unless its complete name matches
`[A-Za-z][A-Za-z0-9_-]*`: the first character must be an ASCII letter and the
remaining characters may be ASCII letters, digits, underscore, or hyphen. The
finding points to the property and says `Name contains unsupported
characters`.

## Review-candidate example

These properties contain spaces, punctuation, or a leading digit:

```yaml
properties:
  "display name": { type: string }
  "customer.email": { type: string }
  "123status": { type: string }
```

Names such as `display_name`, `customer-email`, and `status123` satisfy the
configured grammar.

## Compliant example

```yaml
properties:
  customer_id: { type: string }
  display-name: { type: string }
```

## Parameters, references, and limitations

`match: unsupported-character` selects this fixed grammar; it does not enforce
camelCase, snake_case, or another organisation-specific convention. Matching
is ASCII and case-preserving. The rule does not inspect schema names,
paths, parameters, descriptions, payloads, or runtime consumers. Referenced
schemas count only after host normalisation, and findings may require an
exception for externally defined JSON fields.
