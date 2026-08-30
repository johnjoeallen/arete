---
id: response-code
language: distill
source: Matcher.dsl
scopes:
  - operation
  - response
parameters:
  operation-type:
    type: enum
    required: false
    values: [create, identifiable-resource-retrieval]
  required-status:
    type: integer
    required: false
  status:
    type: integer
    required: false
  expected-status:
    type: integer
    required: false
  match:
    type: enum
    required: false
    values: [semantic-conflict]
  response-shape:
    type: enum
    required: false
    values: [json-object]
  error-format:
    type: enum
    required: false
    values: [problem-json]
---

# Response-code rule

Evaluates documented response status codes from the stable rule model. It
does not infer runtime outcomes or authentication state. Operation-level rules
can require a status; response-level rules can inspect an individual status.
Semantic-conflict is a deliberately narrow first pass: it identifies a 2xx
response whose description contains explicit error wording.
