---
id: SECURITY002
category: Security
detector: security
scope: operation
parameters: { scheme: bearerAuth, scopes: read }
---

# SECURITY002 — Operation security requirement lacks the configured scopes

Protected operations should require the configured security scheme with the
scopes required by the active policy. The detector checks effective OpenAPI
security requirements, including document-level requirements inherited by an
operation. An operation-level requirement replaces the global requirement.

All configured comma-separated scopes must occur in at least one security
requirement for the configured scheme. Security requirements remain
alternatives: another requirement object may satisfy the rule.

## Violation

```yaml
security:
  - bearerAuth: [write]
```

## Compliant

```yaml
security:
  - bearerAuth: [read, write]
```

The scheme and required scopes are policy parameters and may be overridden per
policy.
