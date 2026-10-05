---
id: CONTENT003
category: Content
matcher: media-type
scope: media-type
parameters: { location: response, match: wildcard }
---

# CONTENT003 — Wildcard media type is used

## Intent

Request and response media types should be explicit rather than using
wildcards such as `*/*` or `application/*`. Explicit media types make
negotiation and generated clients predictable.

## Review-candidate example

```yaml
content:
  '*/*':
    schema: { type: object }
```

## Compliant example

```yaml
content:
  application/json:
    schema: { type: object }
```

## Detection and scope

The rule has `media-type` scope and reports request or response content keys
that are `*/*`, end in `/*`, or contain a wildcard.

## Configuration and limitations

`match: wildcard` selects this check. The rule checks documented content keys
and does not assess runtime content negotiation.
