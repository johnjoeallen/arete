---
id: path-count
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  maximum:
    type: integer
    required: true
  maximum-depth:
    type: integer
    required: false
  nested-root:
    type: boolean
    required: false
---

# Path-count rule

Counts distinct first path segments representing top-level resource types.
It can also flag resource paths exceeding a configured nesting depth.
