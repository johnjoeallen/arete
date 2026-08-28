---
id: STATUS001
category: HTTP status
detector: response-code
scope: operation
parameters: { operation-type: create, required-status: 201 }
---

# STATUS001 — Creation operation lacks an appropriate success status

Creation operations should document a status appropriate to synchronous creation. The first pass requires `201`; asynchronous `202` support is a planned descriptor extension.
