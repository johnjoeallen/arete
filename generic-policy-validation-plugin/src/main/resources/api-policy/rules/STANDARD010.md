---
id: STANDARD010
category: Standards
detector: openapi-version
scope: api
parameters: { allowed: "3.0,3.1" }
---

# STANDARD010 — OpenAPI version is unsupported or missing

The API document should declare an OpenAPI version supported by the active
policy. The default policy accepts OpenAPI 3.0 and 3.1 documents.

The parser performs structural validation before this rule runs. This rule
only checks the declared version against the policy's comma-separated
`allowed` prefixes; it does not reimplement the OpenAPI schema validator.

## Violation

```yaml
openapi: 2.0
info:
  title: Customer API
  version: 1.0.0
```

## Compliant

```yaml
openapi: 3.0.3
info:
  title: Customer API
  version: 1.0.0
```

Supported version prefixes may be overridden per policy.
