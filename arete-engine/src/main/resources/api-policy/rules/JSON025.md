---
id: JSON025
category: JSON
matcher: schema-depth
scope: schema
parameters: { minimum: 5 }
---

# JSON025 — Schema nests too deeply

## Intent

A schema that reaches through many levels of objects, by inline definition or by `$ref`, is hard to read, hard to
generate code for and hard to evolve. The rule reports every schema nesting at least {{minimum}} levels and records
the depth, so a policy can charge by how deep the worst one goes (`measure: value` with tiers) and the merge gate can
see a schema getting deeper.

## Diagnostic

A `Order` holding `customer`, holding `address`, holding `geo`, holding `point` is five levels deep and is reported
with the value 5.

## Compliant

Keep the chain below the minimum, or flatten it with identifiers instead of embedded objects.

## Configuration and limitations

`minimum` (default 5 here) is the shallowest depth reported. Depth counts property hops, following `$ref`s; a
recursive schema stops where it recurs. The rule is not part of a bundled policy: a policy opts in with a
`format: 2` entry such as `JSON025: { measure: value, tiers: { 5: 0.5, 7: 1.5 } }`.
