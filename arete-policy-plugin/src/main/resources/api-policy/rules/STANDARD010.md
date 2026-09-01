---
id: STANDARD010
category: Standards
matcher: openapi-version
scope: api
parameters: { allowed: "3.0,3.1" }
---

# STANDARD010 — OpenAPI version is unsupported or missing

## Intent

The API document should declare an OpenAPI version supported by the active
policy. The default policy accepts OpenAPI 3.0 and 3.1 documents.

The parser performs structural scoring before this rule runs. This rule
only checks the declared version against the policy's comma-separated
`allowed` prefixes; it does not reimplement the OpenAPI schema validator.

## Diagnostic

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

## Detection and scope

The rule has `api` scope and uses the `openapi-version` rule:

```yaml
parameters: { allowed: "3.0,3.1" }
```

The rule reads the declared OpenAPI version and accepts it when it equals
one of the comma-separated allowed tokens or starts with an allowed token plus
`.`. A missing or unsupported version produces one finding at `/info` with the
declared value (or `none`). The parser performs structural scoring before
the rule runs.

## Configuration and limitations

The active policy may override `allowed`, for example with `allowed: "3.1"`.
The check concerns the document’s OpenAPI declaration, not `info.version`.
It does not validate the full OpenAPI schema, resolve unsupported features,
inspect server behavior, or convert Swagger 2.0 documents into OpenAPI 3.
Referenced data and runtime compatibility are outside the rule’s scope.
