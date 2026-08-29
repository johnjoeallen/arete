---
id: authentication-error
language: starlark
source: Detector.star
scopes: [operation, response]
parameters:
  status:
    type: integer
    required: false
  required-status:
    type: integer
    required: false
  required-header:
    type: string
    required: false
  forbidden-header:
    type: string
    required: false
---

# Authentication-error detector

Checks authentication and authorization failure contracts. Operation checks
apply only to operations with an effective security requirement; response
checks inspect the configured status and header behavior.
