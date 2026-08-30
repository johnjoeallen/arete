---
id: example-validity
language: distill
source: Matcher.dsl
scopes: [schema, property]
parameters:
  check:
    type: enum
    required: true
    values: [covers-required, satisfies-constraints]
---

# Example-validity rule

Checks that declared `example` values are consistent with their schema.

- `covers-required` — a component-schema `example` that is an object must
  contain every property listed in `required`.
- `satisfies-constraints` — a property `example` must satisfy the property's
  own `pattern`, `minLength` / `maxLength`, `minimum` / `maximum` (honouring
  `exclusiveMinimum` / `exclusiveMaximum`), and `enum`.
