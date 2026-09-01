---
id: openapi-version
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  allowed:
    type: string
    required: true
---

# OpenAPI-version rule

Checks the declared OpenAPI or Swagger document version against a
comma-separated list of supported major/minor versions. Parsing and structural
schema scoring remain the responsibility of the host parser; this rule
reports a missing or unsupported version declaration from the stable model.
