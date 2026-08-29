---
id: HTTP002
category: HTTP
detector: operation-semantics
scope: operation
parameters: { method: POST, match: full-resource-replacement }
---

# HTTP002 — POST is used for complete resource replacement

## Intent

PUT is normally more appropriate when replacing the complete representation of
an identified resource. POST may still be correct for commands or processing
resources, so this rule identifies a design candidate rather than a universal
HTTP violation.

## Detection and scope

The rule has `operation` scope and uses `operation-semantics`:

```yaml
parameters: { method: POST, match: full-resource-replacement }
```

It reports a POST when its path matches an identified-resource shape containing
`/{...}` and the combined path plus summary contains the case-insensitive word
`replace` or `replacement`. The occurrence points to the operation with `POST
appears to replace an identified resource`.

## Review-candidate example

```yaml
paths:
  /customers/{customer_id}:
    post:
      summary: Replace customer
      responses: { '200': { description: Replaced } }
```

Consider PUT if the operation replaces the complete representation, or retain
POST if it is a command with intentionally different semantics.

## Compliant example

This POST targets a collection and does not match the identified-resource
heuristic:

```yaml
paths:
  /customers:
    post:
      summary: Create customer
      responses: { '201': { description: Created } }
```

## Parameters, references, and limitations

The detector inspects only method, path text, and summary. It does not inspect
schemas, status codes, request bodies, descriptions, references, idempotency,
or runtime behavior. It may miss replacement wording outside the recognised
terms and may flag a legitimate command. Findings require design review.
