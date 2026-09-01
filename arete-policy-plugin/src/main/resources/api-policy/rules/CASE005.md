---
id: CASE005
category: Naming
matcher: naming
scope: path-segment
parameters: { convention: kebab-case, match: non-conforming }
---

# CASE005 — Path segment is not kebab-case

## Intent

Multi-word resource path segments should follow a predictable lowercase
kebab-case convention. This improves URL readability and avoids casing and
separator variants across clients.

## Detection and scope

The rule has `path-segment` scope and uses the `naming` rule:

```yaml
parameters: { convention: kebab-case, match: non-conforming }
```

It examines every normalised path segment and reports one whose complete name
does not match `[a-z][a-z0-9]*(?:-[a-z0-9]+)*`. Valid segments begin with a
lowercase letter, may contain lowercase letters or digits, and may separate
words with single hyphens. The finding points to the segment and says `Name
does not use the configured convention`.

## Review-candidate example

These segments do not conform:

```yaml
paths:
  /customerAccounts: { get: { responses: { '200': { description: OK } } } }
  /Customer-accounts: { get: { responses: { '200': { description: OK } } } }
```

`/customer-accounts` is the conventional alternative.

## Compliant example

```yaml
paths:
  /customer-accounts/{customer_id}:
    get: { responses: { '200': { description: OK } } }
```

The host excludes `{customer_id}` parameter segments from this rule’s
path-segment candidates; review parameter naming separately with the
appropriate rule.

## Parameters, references, and limitations

Both configured parameters select the kebab-case check. Matching is ASCII and
case-sensitive; underscores, uppercase letters, empty segments, and leading
digits do not conform. The rule does not inspect methods, schemas,
descriptions, server URLs, or runtime routes. Referenced path items count only
after host normalisation. Findings are naming-policy candidates, and domain
terms or externally fixed paths may require exceptions.
