---
id: CONTENT002
category: Content
matcher: media-type
scope: media-type
parameters: { location: response, match: absent }
---

# CONTENT002 — Response has no documented media type

## Intent

Responses with a representation should declare at least one content media
type. The rule reports responses whose OpenAPI `content` map is empty or
absent.

## Review-candidate example

```yaml
responses:
  '200':
    description: OK
    content: {}
```

## Compliant example

```yaml
responses:
  '200':
    description: OK
    content:
      application/json:
        schema: { type: object }
```

## Detection and scope

The rule has `media-type` scope and reports responses with no documented media
type.

## Configuration and limitations

`location: response` and `match: absent` select this check. The rule does not
require a particular media type or validate the response schema.
