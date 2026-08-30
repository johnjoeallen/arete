---
id: CASE006
category: Naming
matcher: schema-name
scope: schema
parameters: { pattern: "(?i)(definition|response|request|schema|object|model|type|data|payload|dto)[0-9]*" }
---

# CASE006 — Schema name is a placeholder

Component schema names should describe a domain concept. Auto-generated names
such as `Response1` or `InlineObject2` carry no meaning and make generated
clients hard to read.

## Diagnostic

```yaml
components:
  schemas:
    Definition1: { type: object }
    Response2: { type: object }
```

## Compliant

```yaml
components:
  schemas:
    Customer: { type: object }
    CreateOrderResponse: { type: object }
```

## Detection and scope

The rule has `schema` scope and uses the `schema-name` rule. A component
schema whose name matches `pattern` (whole-string, case-insensitive) is
reported once.

## Configuration and limitations

`pattern` is a policy parameter. The rule matches names against a
denylist; it cannot judge whether an arbitrary name is genuinely meaningful.
