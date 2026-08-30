---
id: HTTP006
category: HTTP
matcher: operation-semantics
scope: operation
parameters: { match: inconsistent-method-resource-semantics }
---

# HTTP006 — HTTP method and resource semantics are inconsistent

## Intent

The HTTP method should correspond to the semantics of the operation being
performed on the resource. A method/meaning mismatch can make caching,
idempotency, retries, and client behavior unsafe. This rule uses conservative
textual signals and reports candidates for review rather than proving runtime
behavior.

## Detection and scope

The rule has `operation` scope and uses the `operation-semantics` rule:

```yaml
parameters: { match: inconsistent-method-resource-semantics }
```

For each operation, the rule combines the path and summary and reports
either of these signals:

* a GET whose combined text contains a mutation word (`create`, `update`,
  `delete`, `remove`, `activate`, `deactivate`, `cancel`, `change`, or `set`);
* a POST on a path containing a `{parameter}` whose combined text contains
  `replace` or `replacement`.

The diagnostic points to the operation and says `HTTP method and resource
semantics appear inconsistent`.

## Review-candidate examples

This GET is reported because its summary describes mutation:

```yaml
paths:
  /customers/{customer_id}:
    get:
      summary: Deactivate customer
      responses: { '204': { description: Deactivated } }
```

This POST is reported because it appears to replace an identified resource:

```yaml
paths:
  /customers/{customer_id}:
    post:
      summary: Replace customer
      responses: { '200': { description: Replaced } }
```

## Compliant example

This ordinary retrieval has no mutation signal:

```yaml
paths:
  /customers/{customer_id}:
    get: { summary: Retrieve customer, responses: { '200': { description: OK } } }
```

## Parameters, references, and limitations

`match: inconsistent-method-resource-semantics` selects both heuristics. The
rule does not inspect request/response bodies, status codes, schemas,
descriptions beyond summary, runtime behavior, or actual idempotency. Word
matching is case-insensitive and intentionally limited; legitimate commands
or unusual terminology can be missed or flagged. Unresolved path details may
remove evidence. Findings require human review.
