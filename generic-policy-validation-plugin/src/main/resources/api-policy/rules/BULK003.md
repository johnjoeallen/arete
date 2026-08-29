---
id: BULK003
category: Bulk operations
detector: bulk-operation
scope: operation
parameters:
  method: PUT
  target-selection: search-criteria
---

# BULK003 — Bulk mutation uses search criteria in PUT

## Intent

PUT should normally identify the resource being replaced rather than use
arbitrary search criteria to select multiple resources. A bulk mutation should
make its target set, atomicity, and partial-failure behavior explicit. This
rule is a conservative review heuristic and cannot prove request cardinality.

## Detection and scope

The rule has `operation` scope and uses the `bulk-operation` detector:

```yaml
parameters:
  method: PUT
  target-selection: search-criteria
```

For each operation, the detector lowercases the path and summary, then reports
when the operation method is PUT and that combined text contains `search`,
`filter`, `criteria`, or `query`. The occurrence points to the operation with
`Bulk mutation uses search criteria`.

## Review-candidate example

```yaml
paths:
  /customers:
    put:
      summary: Update customers matching search criteria
      responses: { '204': { description: Updated } }
```

Review whether the operation should target one identified resource, use a
dedicated bulk endpoint, or document selection and failure semantics more
explicitly.

## Compliant example

This PUT has no configured search-term signal:

```yaml
paths:
  /customers/{customer_id}:
    put:
      summary: Replace customer
      responses: { '200': { description: Replaced } }
```

## Parameters, references, and limitations

`method: PUT` and `target-selection: search-criteria` select the check. The
detector inspects only method, path, and summary; it does not inspect query
parameters, request bodies, schemas, response codes, runtime cardinality, or
atomicity. It may miss equivalent selection wording and flag a single-resource
operation whose summary mentions “query.” Referenced operations count only
after host normalisation.
