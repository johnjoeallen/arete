---
id: CONTENT001
category: Content
detector: media-type
scope: media-type
parameters: { location: request, match: absent }
---

# CONTENT001 — Request body has no documented media type

Request bodies should declare at least one content media type in OpenAPI.
This rule checks the contract and does not infer a type from a schema.
