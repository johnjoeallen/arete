---
id: identifier
language: distill
source: Matcher.dsl
scopes: [property]
parameters:
  name-pattern:
    type: string
    required: true
  check:
    type: enum
    required: true
    values: [string, format]
  format:
    type: string
    required: false
---

# Identifier rule

Checks schema properties whose names identify resources. The name pattern and
the expected format are policy parameters; the rule reports contract
metadata only and does not infer identifier uniqueness at runtime.
