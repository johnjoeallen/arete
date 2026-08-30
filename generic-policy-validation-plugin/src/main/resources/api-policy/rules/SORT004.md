---
id: SORT004
category: Collection capabilities
matcher: collection-capability
scope: query-parameter
parameters: { name-pattern: "(^|[-_])(sort|order)([-_]|$)", check: form }
---

# SORT004 — Sort parameter does not use form serialization

Array-valued sort parameters should use standard form serialization unless a
policy explicitly chooses another style.
