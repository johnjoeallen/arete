---
id: hostname
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  convention:
    type: enum
    required: true
    values: [lowercase-hyphenated]
---

# Hostname rule

Checks the host portions of declared OpenAPI server URLs.
