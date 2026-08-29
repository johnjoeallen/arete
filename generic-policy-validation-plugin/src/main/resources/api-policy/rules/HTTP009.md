---
id: HTTP009
category: HTTP
detector: request-body
scope: operation
parameters: { check: forbidden-on-methods, methods: DELETE }
---

# HTTP009 — DELETE operation declares a request body

A `DELETE` should identify the resource to remove through the path. A request
body on `DELETE` is poorly supported by intermediaries and client libraries
and often indicates the operation should be modelled differently.

## Violation

```yaml
/carts/{cartId}/items:
  delete:
    requestBody:
      content:
        application/json:
          schema: { $ref: '#/components/schemas/ItemSelector' }
```

## Compliant

```yaml
/carts/{cartId}/items/{itemId}:
  delete:
    responses:
      '204': { description: Deleted }
```

## Detection and scope

The rule has `operation` scope and uses the `request-body` detector with
`check: forbidden-on-methods` and `methods: DELETE`. An operation whose method
is in the list and that declares a request body is reported.

## Configuration and limitations

`methods` is a policy parameter and may list other methods. The detector
checks for the presence of a request body, not its content.
