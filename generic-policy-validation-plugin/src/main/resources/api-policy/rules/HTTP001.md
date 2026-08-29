---
id: HTTP001
category: HTTP
detector: operation-semantics
scope: operation
parameters: { method: GET, expected: safe }
---

# HTTP001 — GET operation appears to mutate state

## Intent

GET should be safe and should not mutate server state. Safe methods enable
reliable retries, caching, and link prefetching. This rule uses declared names
and summaries only, so it identifies candidates for review rather than proving
runtime mutation.

## Detection and scope

The rule has `operation` scope and uses `operation-semantics`:

```yaml
parameters: { method: GET, expected: safe }
```

For GET operations, the detector combines the path and summary and looks for a
case-insensitive word-boundary mutation term: `create`, `update`, `delete`,
`remove`, `activate`, `deactivate`, `cancel`, `change`, or `set`. Matching
operations are reported at their operation pointer with `GET operation appears
to mutate state`.

## Review-candidate example

```yaml
paths:
  /customers/{customer_id}/activate:
    get:
      summary: Activate customer
      responses: { '204': { description: Activated } }
```

The operation should normally use a state-changing method or be redesigned as
a resource update.

## Compliant example

```yaml
paths:
  /customers/{customer_id}:
    get:
      summary: Retrieve customer
      responses: { '200': { description: Customer returned } }
```

## Parameters, references, and limitations

`method: GET` and `expected: safe` select this heuristic. The detector does
not inspect request bodies, responses, schemas, descriptions, runtime calls,
or actual state changes. It may miss mutation terms not in the fixed list or
flag a read operation whose summary mentions one incidentally. Findings are
review prompts.
