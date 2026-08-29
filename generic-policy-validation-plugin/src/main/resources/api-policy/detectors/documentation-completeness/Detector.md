---
id: documentation-completeness
language: starlark
source: Detector.star
scopes: [property, parameter]
parameters:
  require:
    type: enum
    required: true
    values: [description, example, both]
---

# Documentation-completeness detector

Reports a schema property (scope `property`) or a parameter (scope
`parameter`) that is missing a `description`, an `example`, or both, as
selected by `require`.
