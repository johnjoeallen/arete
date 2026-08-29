---
id: openapi-version
language: groovy
source: Detector.groovy
scopes: [api]
parameters:
  allowed:
    type: string
    required: true
---

# OpenAPI-version detector

Checks the declared OpenAPI or Swagger document version against a
comma-separated list of supported major/minor versions. Parsing and structural
schema validation remain the responsibility of the host parser; this detector
reports a missing or unsupported version declaration from the stable model.
