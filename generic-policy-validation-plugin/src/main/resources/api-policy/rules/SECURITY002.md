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

## Detection and scope

The rule has `operation` scope and uses the `security` detector. For each
operation, it evaluates operation-level security when present; otherwise it
uses the document-level security requirement. An operation-level declaration
replaces, rather than augments, the global declaration. At least one security
requirement object must contain the configured scheme and every configured
scope.

## Configuration and limitations

`scheme: bearerAuth` and `scopes: read` are the default rule parameters. A
policy may override the scheme or provide comma-separated required scopes.
Security requirement objects remain alternatives, so one object satisfying
all scopes is sufficient. The detector does not inspect the security-scheme
definition, token contents, issuer, runtime authentication, or authorization
logic, and it cannot verify that a scope grants the intended business access.
Missing or empty effective security requirements produce a candidate finding.
