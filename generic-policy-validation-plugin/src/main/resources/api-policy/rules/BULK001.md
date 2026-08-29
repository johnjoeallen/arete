---
id: BULK001
category: Bulk operations
detector: bulk-operation
scope: operation
parameters:
  operation-type: create
  expected-method: POST
  payload: collection
---

# BULK001 — Bulk creation is not represented as POST of a collection

Bulk creation should normally POST a collection of entities to an appropriate collection resource. The detector flags create-like operations that do not use the configured method and collection-shaped path.

## Detection and scope

The rule has `operation` scope and uses the `bulk-operation` detector:

```yaml
parameters:
  operation-type: create
  expected-method: POST
  payload: collection
```

The detector lowercases each path and summary and treats an operation as
create-like when the combined text contains `create` or `bulk`. It reports it
when the method is not POST or the path contains a `{parameter}`. The current
detector does not inspect the request body, so `payload: collection` documents
the intended policy but is not independently evaluated.

## Review-candidate example

```yaml
paths:
  /customers/{customer_id}/bulk:
    put:
      summary: Bulk create customers
      responses: { '200': { description: Created } }
```

Review whether creation should POST a collection to `/customers`, or document
why this identified-resource binding is intentional.

## Compliant example

```yaml
paths:
  /customers:
    post:
      summary: Bulk create customers
      requestBody:
        content:
          application/json:
            schema: { type: array, items: { $ref: '#/components/schemas/Customer' } }
      responses: { '201': { description: Created } }
```

This has a create-like summary, POST method, and collection path, so it does
not match the detector’s conditions.

## Parameters, references, and limitations

`operation-type: create`, `expected-method: POST`, and `payload: collection`
are the rule parameters. Only the create type, expected method, path braces,
and create/bulk text currently affect matching. The detector does not prove
bulk cardinality, inspect schemas or bodies, validate responses, or observe
runtime behavior. Referenced operations count only after host normalisation;
findings are review candidates.
