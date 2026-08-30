---
id: metadata
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  required:
    type: enum
    required: true
    values: [complete, identifier, audience]
---

# Metadata rule

Checks the documented OpenAPI information fields. A complete metadata record
has a title, description, contact name and email, and a semantic version.
It can also require the configured `x-api-id` extension.
