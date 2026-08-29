---
id: STANDARD007
category: Standards
detector: resource-path
scope: path
parameters: { match: embedded-identifier }
---

# STANDARD007 — Resource identifier is embedded in a path segment

## Intent

Individual resource identifiers should be represented by dedicated path
segments and path parameters. Separating an identifier from a resource name
can make routing, documentation, and client URL construction clearer.

## Detection and scope

The rule has `path` scope and uses the `resource-path` detector:

```yaml
parameters: { match: embedded-identifier }
```

The detector tokenizes every path by `/` and reports the path if any
non-parameter segment matches the case-sensitive heuristic `Id`, `ID`, or two
or more consecutive digits anywhere in the segment. The occurrence points to
the path and says `Resource identifier is embedded in a path segment`.

## Review-candidate example

Both paths contain an identifier-like value embedded in a resource segment:

```yaml
paths:
  /customers/customerId:
    get: { responses: { '200': { description: OK } } }
  /orders/2024-history:
    get: { responses: { '200': { description: OK } } }
```

A parameterised representation is usually clearer:

```yaml
paths:
  /customers/{customer_id}:
    get:
      parameters:
        - in: path
          name: customer_id
          required: true
          schema: { type: string }
      responses: { '200': { description: OK } }
```

## Compliant example

This ordinary collection path has no identifier-like segment and does not
match:

```yaml
paths:
  /customers:
    get: { responses: { '200': { description: Customer collection } } }
```

## Parameters, references, and limitations

The rule has no configurable threshold or naming convention beyond the fixed
`embedded-identifier` match. Segments beginning with `{` are excluded from
the embedded check, and the detector does not inspect parameter definitions,
operation methods, schemas, or descriptions. It may flag legitimate names
such as `version2024` and may miss identifiers using another convention.
Referenced path items and runtime routing are not inferred; findings are
heuristic candidates for review.
