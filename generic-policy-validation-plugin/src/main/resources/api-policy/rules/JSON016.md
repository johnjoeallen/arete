---
id: JSON016
category: JSON
detector: response-code
scope: response
parameters: { response-shape: json-object }
---

# JSON016 — Successful response is not a JSON object

Successful JSON responses should use an object as their top-level
representation. Objects provide a stable envelope that can gain fields later
without changing the shape clients already consume. A bare scalar or array
usually forces a breaking change when additional metadata is needed.

## What this rule checks

For each documented `2xx` response, the detector examines the schemas attached
to the response content. If a response schema is present and its top-level type
is not `object`, the response is reported. The rule is concerned with the
contract declared in OpenAPI; it does not inspect runtime payloads.

The rule applies to every successful status from `200` through `299` and is
content-shape based. It does not require a particular property set inside the
object, nor does it reject an object whose properties are themselves arrays or
scalars.

## Examples

### Violation

```yaml
responses:
  '200':
    content:
      application/json:
        schema:
          type: array
          items:
            type: string
```

### Compliant

```yaml
responses:
  '200':
    content:
      application/json:
        schema:
          type: object
          properties:
            customers:
              type: array
              items:
                $ref: '#/components/schemas/Customer'
```

Responses without a documented schema are not flagged by this rule: the
detector cannot infer a top-level shape that the contract does not declare.

## Payload examples

### Bad — top-level array

```json
[
  { "id": "c-123", "name": "Alice" },
  { "id": "c-456", "name": "Bob" }
]
```

### Bad — top-level scalar

```json
"created"
```

### Good — object envelope

```json
{
  "customers": [
    { "id": "c-123", "name": "Alice" },
    { "id": "c-456", "name": "Bob" }
  ],
  "count": 2
}
```

The object envelope leaves room for fields such as `count`, pagination links,
or warnings to be added without changing the response from an array or scalar
into a different top-level JSON shape.
