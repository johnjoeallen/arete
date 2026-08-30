---
id: PAGE006
category: Pagination
matcher: pagination
scope: query-parameter
parameters: { name-pattern: "(^|[-_])cursor([-_]|$)", check: string }
---

# PAGE006 — Cursor parameter is not a string

Cursor-based pagination controls should use strings so opaque cursors can be
changed without imposing a numeric representation on clients.
