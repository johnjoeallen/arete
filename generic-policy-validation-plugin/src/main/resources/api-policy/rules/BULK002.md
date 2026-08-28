---
id: BULK002
category: Bulk operations
detector: schema
scope: property
parameters:
  type: array
  max-items: absent
---

# BULK002 — Bulk request array has no maximum size

Bulk request arrays should normally place an explicit bound on the number of items accepted in one request.
