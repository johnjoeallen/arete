---
id: parameter
language: distill
source: Matcher.dsl
scopes: [operation, parameter]
parameters:
  check:
    type: enum
    required: true
    values: [max-count, path-required, template-match, schema-present]
  maximum:
    type: integer
    required: false
---

# Parameter rule

Inspects the operation and path parameters declared in the contract.

- `max-count` — reports an operation that declares more than `maximum`
  parameters (path and operation parameters combined).
- `path-required` — reports a path parameter that is not marked
  `required: true`.
- `template-match` — reports a path parameter with no matching `{placeholder}`
  in the path template, and a `{placeholder}` with no matching path parameter.
- `schema-present` — reports a parameter that declares neither `schema` nor
  `content`.
