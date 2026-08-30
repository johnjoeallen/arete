---
id: PAGE002
category: Pagination
matcher: pagination
scope: query-parameter
parameters: { name-pattern: "(^|[-_])(page|offset)([-_]|$)", check: integer }
---

# PAGE002 — Page or offset parameter is not an integer

Offset-based pagination controls should declare an integer schema.
