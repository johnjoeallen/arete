---
id: HTTP005
category: HTTP
detector: operation
scope: operation
parameters: { method: GET, request-body: present }
---

# HTTP005 — GET operation has a request body

GET request bodies should normally be avoided because their semantics and interoperability are poorly defined.
