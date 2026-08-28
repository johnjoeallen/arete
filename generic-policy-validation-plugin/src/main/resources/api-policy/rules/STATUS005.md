---
id: STATUS005
category: HTTP status
detector: response-code
scope: response
parameters: { match: semantic-conflict }
---

# STATUS005 — Status code conflicts with operation semantics

HTTP response status codes should accurately communicate the outcome of the operation. This initial detector flags explicit error wording in a 2xx response.
