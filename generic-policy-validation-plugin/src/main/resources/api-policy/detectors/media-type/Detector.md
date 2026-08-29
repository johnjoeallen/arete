---
id: media-type
language: groovy
source: Detector.groovy
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

# Media-type detector

Checks request-body and response content media types from the stable OpenAPI
model. `absent` reports a body or response with no documented content,
`wildcard` reports media types such as `*/*` or `application/*`, and
`not-allowed` reports media types outside the configured comma-separated
allow-list. The detector does not inspect runtime negotiation or payloads.
