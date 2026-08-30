---
id: query-collection
language: distill
source: Matcher.dsl
scopes: [query-parameter]
parameters:
  style:
    type: enum
    required: true
    values: [form, spaceDelimited, pipeDelimited, deepObject]
  explode:
    type: boolean
    required: true
---

# Query-collection rule

Checks the serialization style and explode setting of array-valued query
parameters. When OpenAPI omits these fields, the rule applies the
OpenAPI defaults: `form` style and `explode: true` for form-style arrays;
other styles default to `explode: false`.
