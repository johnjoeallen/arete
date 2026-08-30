---
id: STANDARD002
category: Standards
matcher: resource-path
scope: path
parameters: { match: trailing-slash }
---

# STANDARD002 — Resource path has a trailing slash

## Intent

Resource paths should not end with an unnecessary trailing slash. A single
canonical spelling avoids duplicate cache keys, redirects, and client routing
mistakes. Some servers intentionally distinguish slash and no-slash routes,
so this rule is a convention for review.

## Detection and scope

The rule has `path` scope and uses the `resource-path` rule:

```yaml
parameters: { match: trailing-slash }
```

It reports every path longer than one character whose path string ends with
`/`. The diagnostic points to the path and says `Resource path has an
unnecessary trailing slash`. The rule does not compare the path with a
second canonical route.

## Review-candidate example

This path is reported:

```yaml
paths:
  /customers/:
    get: { responses: { '200': { description: Customer collection } } }
```

The API should normally choose `/customers` as its canonical path, or document
why the trailing slash is intentional.

## Compliant example

The equivalent path without a trailing slash does not match:

```yaml
paths:
  /customers:
    get: { responses: { '200': { description: Customer collection } } }
```

The root path `/` is also excluded by the rule’s length check.

## Parameters, references, and limitations

`match: trailing-slash` selects this behavior. The rule does not inspect
servers, redirects, URL encoding, path parameters, runtime routing, or whether
the slash is meaningful to a gateway. Referenced path items are considered
only when present in the host’s normalised path collection. It reports the
declared path once regardless of how many operations it contains.
