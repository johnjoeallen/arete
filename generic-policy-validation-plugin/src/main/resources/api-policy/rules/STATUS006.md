---
id: STATUS006
category: Status codes
detector: response-code
scope: response
parameters: { error-format: problem-json }
---

# STATUS006 — Error response lacks Problem Details

## Intent

Error responses should provide a consistent machine-readable representation so
clients can handle failures without parsing endpoint-specific prose. This rule
checks for the standard `application/problem+json` media type. It is a policy
convention: an API may intentionally use another documented error format.

## Detection and scope

The rule has `response` scope and uses the `response-code` detector with:

```yaml
parameters: { error-format: problem-json }
```

For each documented response with a status from 400 through 599, the detector
reports when `application/problem+json` is not present in the operation’s
normalised media-type facts. The occurrence points to the operation’s path
pointer and says `Error response does not declare application/problem+json`.
The current detector does not identify which response was missing the media
type in the occurrence.

## Review-candidate example

This error response is reported:

```yaml
paths:
  /customers:
    get:
      responses:
        '404':
          description: Customer not found
          content:
            application/json: { schema: { type: object } }
```

An appropriate Problem Details response would be represented on the wire as:

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{"type":"https://example.test/problems/not-found","title":"Not found","status":404}
```

## Compliant example

Declaring the media type on the error response satisfies the detector:

```yaml
responses:
  '400':
    description: Invalid request
    content:
      application/problem+json:
        schema: { $ref: '#/components/schemas/Problem' }
```

## Parameters, references, and limitations

`error-format: problem-json` selects this check; it does not validate the
Problem Details schema, required fields, status consistency, or response
body. Referenced responses and content are considered only insofar as the
host resolves them into normalised facts. The detector uses the operation’s
aggregate media-type facts, so another media type on the same operation may
currently satisfy the check even when an individual error response omits
Problem JSON. It does not inspect runtime headers, examples, descriptions, or
non-error 2xx/3xx responses.
