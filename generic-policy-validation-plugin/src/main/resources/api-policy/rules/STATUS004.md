---
id: STATUS004
category: HTTP status
detector: response-code
scope: operation
parameters: { operation-type: identifiable-resource-retrieval, required-status: 404 }
---

# STATUS004 — Resource retrieval lacks a not-found response

Retrieving an individually identifiable resource should normally document the outcome when that resource does not exist.
