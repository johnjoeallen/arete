---
id: HTTP003
category: HTTP
matcher: operation-semantics
scope: operation
parameters: { method: PUT, match: partial-update }
---

# HTTP003 — PUT appears to perform a partial modification

## Intent

PUT should normally represent complete replacement of the target resource. A
partial PUT can make omitted-field behavior and retries ambiguous. This rule is
a summary/path heuristic and cannot establish server behavior.

## Detection and scope

The rule has `operation` scope and uses `operation-semantics`:

```yaml
parameters: { method: PUT, match: partial-update }
```

For PUT operations, the rule combines the path and summary and performs a
case-insensitive word-boundary search for `partial`, `patch`, or `update`.
Matching operations are reported at their operation pointer with `PUT appears
to perform a partial update`.

## Review-candidate example

```yaml
paths:
  /customers/{customer_id}:
    put:
      summary: Update selected customer fields
      responses: { '204': { description: Updated } }
```

Review whether this should be PATCH or whether the summary should clarify full
replacement semantics.

## Compliant example

```yaml
paths:
  /customers/{customer_id}:
    put:
      summary: Replace customer
      responses: { '200': { description: Replaced } }
```

## Parameters, references, and limitations

The method and match parameters are fixed by the rule. Schemas, descriptions,
request bodies, responses, references, and runtime traffic are not inspected.
Different wording may be missed and harmless uses of “update” may be flagged;
findings require human review.
