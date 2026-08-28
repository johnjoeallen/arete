---
id: VERSION001
category: Versioning
detector: versioning
scope: path
parameters: { location: uri, match: present }
---

# VERSION001 — Version appears in the URI

The API encodes its interface version directly in a resource URI, such as `/v2/customers`. This is a policy choice.
