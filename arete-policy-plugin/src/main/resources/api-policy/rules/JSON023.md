---
id: JSON023
category: JSON
matcher: array-items
scope: api
---

# JSON023 — Array schema declares no items

## Intent

An array schema must say what its elements are. OpenAPI requires `items` on
every `type: array` schema; without it the element type is undefined. Code
generators emit `List<Object>` or fail outright, request/response validators
accept anything, and the rendered documentation shows an array of nothing.
A missing `items` is almost always an editing slip rather than a deliberate
"any value" array.

## Detection and scope

The rule has `api` scope and uses the `array-items` matcher. Two shapes are
checked:

- a component schema under `components/schemas` whose `type` is `array`, and
- an object property whose `type` is `array`,

each reported once, at its own pointer, when `items` is absent.

## Diagnostic

```yaml
components:
  schemas:
    TagList:
      type: array          # no items
    Product:
      type: object
      properties:
        categories:
          type: array      # no items
```

Both `TagList` and `Product.categories` are reported.

## Compliant

```yaml
components:
  schemas:
    TagList:
      type: array
      items: { type: string }
    Product:
      type: object
      properties:
        categories:
          type: array
          items: { $ref: '#/components/schemas/Category' }
```

## Configuration and limitations

The rule takes no parameters. It reads the normalised model's `array` flag and
an `itemsPresent` flag; it does not resolve `items` references or inspect the
element schema's contents. Tuple-style arrays (`prefixItems`, OpenAPI 3.1) are
outside the current model and are not evaluated.
