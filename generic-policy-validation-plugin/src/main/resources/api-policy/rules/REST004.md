---
id: REST004
category: Resource design
detector: resource-path
scope: operation
parameters: { match: custom-action }
---

# REST004 — Custom action resource is used

## Intent

Custom actions should be avoided where an operation can be represented
naturally using resources and standard HTTP methods. Action endpoints can be
appropriate for domain commands, but they may make resource discovery,
authorization, and HTTP semantics less uniform. This rule identifies a
candidate for design review rather than banning commands.

## Detection and scope

The rule has `operation` scope and uses the `resource-path` detector:

```yaml
parameters: { match: custom-action }
```

The detector matches paths, case-insensitively, against a pattern equivalent
to `/actions` at the end of a path, optionally followed by one additional
segment. Thus `/customers/actions` and `/customers/actions/archive` match.
Every operation on a matching path is reported at its operation pointer with
`Custom action resource is used`.

## Review-candidate example

This endpoint is reported:

```yaml
paths:
  /customers/{customer_id}/actions/deactivate:
    post:
      summary: Deactivate customer
      responses: { '204': { description: Deactivated } }
```

Review whether deactivation should instead be represented as a resource state
change, such as a PATCH to the customer or a dedicated status resource.

## Compliant example

This path does not contain the detector’s `/actions` form:

```yaml
paths:
  /customers/{customer_id}:
    patch:
      summary: Deactivate customer
      responses: { '204': { description: Updated } }
```

The absence of a finding does not establish that the operation follows REST
semantics; it only means this path pattern was not matched.

## Parameters, references, and limitations

`match: custom-action` is the rule’s only configured behavior. The detector
does not inspect methods, summaries, schemas, request bodies, or runtime
behavior. It does not match arbitrary command-like segments unless they use
the recognised `actions` path form. Referenced path items count only when the
host resolves them into its normalised path collection. Findings are heuristic
design prompts.
