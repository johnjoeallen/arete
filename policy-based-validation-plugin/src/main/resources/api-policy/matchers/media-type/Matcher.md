---
id: media-type
language: distill
source: Matcher.dsl
scopes: [media-type]
parameters:
  location:
    type: enum
    required: true
    values: [request, response]
  match:
    type: enum
    required: true
    values: [absent, wildcard, not-allowed]
  allowed:
    type: string
    required: false
---

# Media-type rule

Checks request-body and response content media types from the stable OpenAPI
model. `location` selects the collection (`api.operations` or `api.responses`)
and each `match` value is one `checks(...)` stanza that reports the subject when
it **violates** that check: `absent` reports a body or response with no
documented content, `wildcard` reports media types such as `*/*` or
`application/*`, and `not-allowed` reports media types outside the configured
comma-separated allow-list. The rule does not inspect runtime negotiation or
payloads.
