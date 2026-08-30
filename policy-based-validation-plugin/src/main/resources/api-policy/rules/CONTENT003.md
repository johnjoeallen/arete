---
id: CONTENT003
category: Content
matcher: media-type
scope: media-type
parameters: { location: response, match: wildcard }
---

# CONTENT003 — Wildcard media type is used

Request and response media types should be explicit rather than using
wildcards such as `*/*` or `application/*`. Explicit media types make
negotiation and generated clients predictable.
