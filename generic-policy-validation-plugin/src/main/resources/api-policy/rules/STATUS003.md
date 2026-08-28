---
id: STATUS003
category: HTTP status
detector: response-code
scope: response
parameters: { status: 403, expected-status: 401 }
---

# STATUS003 — Authentication failure uses an inappropriate status

This first pass flags documented `403` responses as candidates for review where the policy expects authentication failures to use `401`.
