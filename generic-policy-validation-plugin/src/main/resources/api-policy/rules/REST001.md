---
id: REST001
category: Resource design
detector: resource-path
scope: path
parameters:
  match: operation-verb
---

# REST001 — Resource path contains an operation verb

## Intent

Resource paths should identify resources rather than describe operations.
Standard HTTP methods should express the operation being performed, producing
more uniform URLs and allowing clients to apply familiar HTTP semantics.

## Detection and scope

The rule has `path` scope and uses the `resource-path` detector with:

```yaml
parameters:
  match: operation-verb
```

The detector looks only at the terminal path segment. After lowercasing it, it
reports the path when the segment starts with one of `get`, `list`, `create`,
`update`, `delete`, `remove`, `add`, or `set`. Matching is prefix-based, so
names such as `getCustomers` also match. Every operation on a matching path is
reported at its operation pointer with `Resource path contains an operation
verb`.

## Review-candidate examples

These paths are reported:

```yaml
paths:
  /getCustomers:
    get: { responses: { '200': { description: Customers } } }
  /createCustomer:
    post: { responses: { '201': { description: Created } } }
  /deleteCustomer/{customer_id}:
    delete: { responses: { '204': { description: Deleted } } }
```

The resource-oriented alternatives are `/customers` for collection creation
or retrieval and `/customers/{customer_id}` for an individual customer.

## Compliant example

The terminal segment `customers` does not start with one of the configured
verbs:

```yaml
paths:
  /customers:
    get: { responses: { '200': { description: Customers } } }
```

## Parameters, references, and limitations

`match: operation-verb` selects the fixed prefix list. The rule does not
inspect the HTTP method, summary, schemas, descriptions, or runtime routing.
It may flag legitimate resource names such as `update-history`, and it does
not catch verbs in non-terminal segments unless the terminal segment also
matches. Referenced path items are considered only after host normalisation.
This heuristic should guide design review rather than dictate renaming.

## Poor

`GET /getCustomers`

`POST /createCustomer`

`DELETE /deleteCustomer/123`

## Better

`GET /customers`

`POST /customers`

`DELETE /customers/123`
