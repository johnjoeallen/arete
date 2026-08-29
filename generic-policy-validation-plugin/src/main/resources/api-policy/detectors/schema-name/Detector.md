---
id: schema-name
language: starlark
source: Detector.star
scopes: [schema]
parameters:
  pattern:
    type: string
    required: true
  case:
    type: enum
    required: false
    values: [pascal-case]
---

# Schema-name detector

Reports a component schema whose name matches `pattern` (RE2 syntax,
whole-string match).

- Without `case`, every match is reported — used to flag placeholder names
  such as `Object1` or `Response2` that carry no domain meaning.
- With `case: pascal-case`, only a matching name that is *not* PascalCase is
  reported — used to require `CustomerResponse` rather than `customer_response`
  for request/response objects.
