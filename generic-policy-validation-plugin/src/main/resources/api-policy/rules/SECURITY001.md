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

## Detection and scope

The rule has `operation` scope and uses the `security` detector. For each
operation, an operation-level `security` declaration is used when present;
otherwise the document-level declaration is inherited. The detector reports
when no alternative security requirement object contains the configured scheme.
With no scopes configured, the scheme’s presence is sufficient. An explicit
empty `security: []` is treated as anonymous and is reported.

## Configuration and limitations

`scheme: bearerAuth` is the default parameter and may be overridden by a
policy. A scheme entry with any scope list satisfies SECURITY001; SECURITY002
can require particular scopes. The detector does not inspect the security
scheme definition, token validity, issuer, runtime middleware, or
authorization decisions. It checks the normalised OpenAPI contract only, and
referenced security data counts only when resolved by the host.
