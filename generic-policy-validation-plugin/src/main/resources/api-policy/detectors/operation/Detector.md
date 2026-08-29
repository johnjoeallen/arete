---
id: operation
language: starlark
source: Detector.star
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
  request-body:
    type: enum
    required: false
    values:
      - present
      - absent
---

# Operation detector

Inspects each OpenAPI operation through the stable detector API. It supports
method selection and checks for an operation summary or request body. Rules
may combine these parameters; every supplied condition must match.

The detector reports operation occurrences only. It cannot inspect the active
policy and never calculates severity or score.
