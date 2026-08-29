---
id: SECURITY001
category: Security
detector: security
scope: operation
parameters: { scheme: bearerAuth }
---

# SECURITY001 — Operation does not require the configured security scheme

Every operation should require the security scheme selected by the active
policy. The detector checks effective OpenAPI security requirements: an
operation-level `security` declaration overrides the document-level value,
while an operation without its own declaration inherits the global value.

An explicit empty `security: []` makes an operation anonymous and is reported.
The rule checks the contract only; it does not verify credentials or runtime
authorization.

## Violation

```yaml
paths:
  /customers:
    get:
      security: []
```

## Compliant

```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
paths:
  /customers:
    get:
      security:
        - bearerAuth: []
```

The required scheme is a policy parameter and may be overridden per policy.
