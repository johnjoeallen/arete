---
id: operation
language: distill
source: Matcher.dsl
scopes:
  - operation
parameters:
  method:
    type: enum
    required: false
    values:
      - GET
      - POST
      - PUT
      - PATCH
      - DELETE
      - HEAD
      - OPTIONS
  summary:
    type: enum
    required: false
    values:
      - present
      - absent
  description:
    type: enum
    required: false
    values:
      - present
      - absent
  request-body:
    type: enum
    required: false
    values:
      - present
      - absent
---

# Operation rule

Inspects each OpenAPI operation through the stable rule API. It supports
method selection and checks for an operation summary or request body. Rules
may combine these parameters; every supplied condition must match.

The rule reports operation diagnostics only. It cannot inspect the active
policy and never calculates severity or score.
