---
id: HTTP004
category: HTTP
detector: operation
scope: operation
parameters: { method: DELETE, request-body: present }
---

# HTTP004 — DELETE operation has a request body

DELETE should normally identify its target resource through the URI rather than depend on a request body.
