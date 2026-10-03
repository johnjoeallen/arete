---
id: PAGE004
category: Pagination
matcher: pagination
scope: query-parameter
parameters: { name-pattern: "(^|[-_])limit([-_]|$)", check: maximum, maximum: 100 }
---

# PAGE004 — Page-size parameter lacks a safe maximum

## Intent

Pagination limits should declare a maximum so a client cannot request an
unbounded page.

## Review-candidate example

```yaml
schema:
  type: integer
```

## Compliant example

```yaml
schema:
  type: integer
  maximum: 100
```

## Detection and scope

The rule has `query-parameter` scope and reports a matching page-size
parameter when its schema has no maximum or its maximum is greater than 100.

## Configuration and limitations

`check: maximum` and `maximum: 100` select this constraint. The rule does not
require a minimum or assess server-side enforcement of the limit.
