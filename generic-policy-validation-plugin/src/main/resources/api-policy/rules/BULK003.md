---
id: BULK003
category: Bulk operations
detector: bulk-operation
scope: operation
parameters:
  method: PUT
  target-selection: search-criteria
---

# BULK003 — Bulk mutation uses search criteria in PUT

PUT should normally identify the resource being replaced rather than use arbitrary search criteria to select multiple resources.
