---
id: REST001
category: Resource design
detector: resource-path
scope: path
parameters:
  match: operation-verb
---

# REST001 — Resource path contains an operation verb

Resource paths should identify resources rather than actions. Prefer
`GET /customers` to `GET /getAllCustomers`.
