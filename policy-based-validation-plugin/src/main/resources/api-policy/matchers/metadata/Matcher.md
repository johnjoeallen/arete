---
id: metadata
language: distill
source: Matcher.dsl
scopes: [api]
parameters:
  required:
    type: enum
    required: true
    values: [complete, identifier, audience, license]
---

# Metadata rule

Checks the documented OpenAPI information fields.

- `complete` — a complete metadata record has a title, description, contact
  name and email, and a semantic version.
- `identifier` — requires the `x-api-id` extension.
- `audience` — requires the `x-audience` extension.
- `license` — requires an `info.license` with both a `name` and a `url`.
