---
id: CONTENT004
category: Content
matcher: media-type
scope: media-type
parameters: { location: response, match: not-allowed, allowed: "application/json,application/problem+json,text/plain" }
---

# CONTENT004 — Media type is outside the configured allow-list

## Intent

Documented response media types should use the names allowed by the active
policy. Matching is case-insensitive and parameters such as `; charset=utf-8`
are treated as part of the media-type name because OpenAPI content keys are
not runtime header values.

## Review-candidate example

```yaml
content:
  text/xml:
    schema: { type: string }
```

## Compliant example

```yaml
content:
  application/json:
    schema: { type: object }
```

## Detection and scope

The rule has `media-type` scope and reports response content types that are not
in the configured allow-list.

## Configuration and limitations

`match: not-allowed` and `allowed` select the allow-list. Matching is
case-insensitive but otherwise exact; media-type parameters are not normalised.
