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
      - absent
  description:
    type: enum
    required: false
    values:
      - absent
  request-body:
    type: enum
    required: false
    values:
      - present
      - absent
---

# Operation rule

Inspects each OpenAPI operation through the stable rule API. It selects
operations by method, by a missing summary or description, or by the presence
or absence of a request body. Rules may combine these parameters; every
supplied condition must match, and a rule that supplies none matches nothing.

The rule reports operation diagnostics only. It cannot inspect the active
policy and never calculates severity or score.
