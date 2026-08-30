---
id: schema-composition
language: distill
source: Matcher.dsl
scopes: [schema, operation]
parameters:
  check:
    type: enum
    required: true
    values: [inline-composition, inline-body]
---

# Schema-composition rule

- `inline-composition` — reports a component schema that uses `allOf`,
  `anyOf`, or `oneOf` with one or more members declared inline instead of
  through `$ref`.
- `inline-body` — reports an operation whose request body declares an inline
  object schema (properties, no `$ref`) rather than referencing a reusable
  component; response bodies with an inline object schema are reported too.
