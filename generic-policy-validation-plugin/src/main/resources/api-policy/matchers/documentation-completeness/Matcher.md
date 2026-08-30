---
id: documentation-completeness
language: distill
source: Matcher.dsl
scopes: [property, parameter]
parameters:
  require:
    type: enum
    required: true
    values: [description, example, both]
---

# Documentation-completeness rule

Reports a schema property (scope `property`) or a parameter (scope
`parameter`) that is missing a `description`, an `example`, or both, as
selected by `require`.
