---
id: STANDARD003
category: Standards
detector: response-header
scope: response
parameters: { status: 200, header: Link, required: false }
---

# STANDARD003 — Response contains a Link header

JSON API responses should not use HTTP Link headers for application-level relationships.
