---
id: PAGE003
category: Pagination
detector: pagination
scope: query-parameter
parameters: { name-pattern: "(^|[-_])limit([-_]|$)", check: integer }
---

# PAGE003 — Page-size parameter is not an integer

A page-size limit should be expressed as an integer query parameter.
