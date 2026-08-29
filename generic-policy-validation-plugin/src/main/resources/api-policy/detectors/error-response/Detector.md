---
id: error-response
language: starlark
source: Detector.star
scopes: [operation, response]
parameters:
  required-class:
    type: enum
    required: false
    values: [success, client-error, server-error]
  require-description:
    type: boolean
    required: false
  problem-json:
    type: boolean
    required: false
  status:
    type: integer
    required: false
  required-header:
    type: string
    required: false
---

# Error-response detector

Checks documented HTTP error and success responses using only the stable
operation model. Operation checks report missing status classes; response
checks validate descriptions, representations, or required headers.
