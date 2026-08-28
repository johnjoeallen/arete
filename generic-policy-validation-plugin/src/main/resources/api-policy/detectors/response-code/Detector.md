---
id: response-code
language: groovy
source: Detector.groovy
scopes:
  - operation
  - response
parameters:
  operation-type:
    type: enum
    required: false
    values: [create, identifiable-resource-retrieval]
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
---

# Response-code detector

Evaluates documented response status codes from the stable detector model. It
does not infer runtime outcomes or authentication state. Operation-level rules
can require a status; response-level rules can inspect an individual status.
Semantic-conflict is a deliberately narrow first pass: it identifies a 2xx
response whose description contains explicit error wording.
