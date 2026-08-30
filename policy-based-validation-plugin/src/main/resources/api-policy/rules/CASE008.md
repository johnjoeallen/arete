---
id: CASE008
category: Naming
matcher: tag
scope: api
parameters: { check: name-convention, convention: kebab-case }
---

# CASE008 — Tag name does not follow the naming convention

## Intent

Tag names group operations in generated documentation and client SDKs. A
single, predictable convention keeps navigation consistent and the generated
symbols stable. This rule checks tag names against `kebab-case` by default;
a policy can set a different convention.

## Detection and scope

The rule has `api` scope and uses the `tag` matcher with
`check: name-convention`. It collects the distinct tag names referenced by
operations and reports each one that does not match the configured
convention.

## Review-candidate example

`convention: kebab-case`

```yaml
paths:
  /orders:
    get:
      tags: [Order Management]
      responses: { '200': { description: OK } }
```

`Order Management` is reported; `order-management` satisfies the convention.

## Parameters, references, and limitations

`convention` accepts `camelCase`, `snake_case`, `kebab-case`, or `hyphenated`.
The rule inspects tag names as used on operations, not the top-level `tags`
list. It is a naming aid, not a linguistic authority.
