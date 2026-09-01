---
id: parameter
language: distill
source: Matcher.dsl
scopes: [operation, parameter]
parameters:
  check:
    type: enum
    required: true
    values: [max-count, path-required, template-match, schema-present, unique]
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
- `unique` — reports a parameter whose `name` + `in` pair is declared more
  than once on the same operation (counting path-level and operation-level
  parameters together). The first declaration is left alone; each later
  duplicate is reported.
