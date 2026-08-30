---
id: HTTP008
category: HTTP
matcher: operation-semantics
scope: path
parameters: { match: unsupported-operation-semantics-unclear }
---

# HTTP008 — Supported operation semantics are unclear

## Intent

Every operation should have semantics that clients and tooling can understand
from its HTTP method and documented resource. Unsupported or ambiguous methods
would merit review, but the current stable OpenAPI model exposes only standard
methods.

## Detection and scope

The rule has `path` scope and uses the `operation-semantics` rule:

```yaml
parameters: { match: unsupported-operation-semantics-unclear }
```

The rule’s `unsupported-operation-semantics-unclear` vocabulary is
currently a no-op: it returns no diagnostics for the standard OpenAPI
operations represented by the host. Therefore HTTP008 produces no automated
findings in the current implementation.

## Examples and review guidance

Standard operations such as this are accepted without an HTTP008 diagnostic:

```yaml
paths:
  /customers:
    get: { responses: { '200': { description: Customer collection } } }
```

This rule does not currently flag a non-standard method if a parser or host
cannot represent it. Reviewers should assess such operations manually until a
stable model extension exists.

## Parameters, references, and limitations

`match: unsupported-operation-semantics-unclear` selects the currently empty
branch. The rule does not inspect descriptions, schemas, runtime behavior,
custom HTTP methods, or server routing, and it cannot infer unclear semantics
from an otherwise valid operation. References and missing information do not
create diagnostics. This documentation records a policy capability reserved
for a future rule extension, not an active automated check.
