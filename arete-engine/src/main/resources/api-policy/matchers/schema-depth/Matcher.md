---
id: schema-depth
language: distill
source: Matcher.distill
scopes: [schema]
parameters:
  minimum:
    type: integer
    required: false
---

# Schema-depth rule

Reports each component schema whose longest chain of property hops, following `$ref`s, is at least `minimum`
(default 1, so every schema holding a property). Each occurrence carries the depth as its measured value, so a
policy can charge by the deepest schema with `measure: value`.
