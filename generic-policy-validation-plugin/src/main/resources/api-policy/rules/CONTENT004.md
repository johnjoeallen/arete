---
id: CONTENT004
category: Content
matcher: media-type
scope: media-type
parameters: { location: response, match: not-allowed, allowed: "application/json,application/problem+json,text/plain" }
---

# CONTENT004 — Media type is outside the configured allow-list

Documented response media types should use the names allowed by the active
policy. Matching is case-insensitive and parameters such as `; charset=utf-8`
are treated as part of the media-type name because OpenAPI content keys are
not runtime header values.
