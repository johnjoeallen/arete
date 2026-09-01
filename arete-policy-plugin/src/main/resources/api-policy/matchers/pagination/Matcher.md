---
id: pagination
language: distill
source: Matcher.dsl
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

# Pagination rule

Checks documented pagination controls on collection `GET` operations and their
responses. Parameter names, page-size limits, and response link requirements
are policy-controlled.
