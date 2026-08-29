---
id: schema-name
language: starlark
source: Detector.star
scopes: [schema]
parameters:
  pattern:
    type: string
    required: true
---

# Schema-name detector

Reports a component schema whose name matches `pattern` (RE2 syntax,
whole-string match). Policies use it to flag placeholder names such as
`Object1` or `Response2` that carry no domain meaning.
