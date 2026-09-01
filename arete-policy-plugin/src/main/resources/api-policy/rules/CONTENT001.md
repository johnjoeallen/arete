---
id: CONTENT001
category: Content
matcher: media-type
scope: media-type
parameters: { location: request, match: absent }
---

# CONTENT001 — Request body has no documented media type

## Intent

Request bodies should declare at least one content media type in OpenAPI.
This rule checks the contract and does not infer a type from a schema.

## Review-candidate example

```yaml
requestBody:
  required: true
  content: {}
```

## Compliant example

```yaml
requestBody:
  content:
    application/json:
      schema: { type: object }
```

## Detection and scope

The rule has `media-type` scope and reports operations whose request body has
no documented media type.

## Configuration and limitations

`location: request` and `match: absent` select this check. The rule does not
infer a media type from a schema or implementation behaviour.
