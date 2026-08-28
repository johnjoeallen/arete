---
id: STATUS002
category: HTTP status
detector: response-header
scope: response
parameters: { status: 201, header: Location, required: true }
---

# STATUS002 — Created resource response lacks location information

Where a newly created resource has an addressable URI, a `201 Created` response should expose its location.
