---
id: pagination
language: starlark
source: Detector.star
scopes: [operation, query-parameter, response]
parameters:
  name-pattern:
    type: string
    required: true
  check:
    type: enum
    required: true
    values: [present, integer, string, maximum, link]
  maximum:
    type: integer
    required: false
---

# Pagination detector

Checks documented pagination controls on collection `GET` operations and their
responses. Parameter names, page-size limits, and response link requirements
are policy-controlled.
