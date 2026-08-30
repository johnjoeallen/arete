---
id: CONTENT002
category: Content
matcher: media-type
scope: media-type
parameters: { location: response, match: absent }
---

# CONTENT002 — Response has no documented media type

Responses with a representation should declare at least one content media
type. The rule reports responses whose OpenAPI `content` map is empty or
absent.
