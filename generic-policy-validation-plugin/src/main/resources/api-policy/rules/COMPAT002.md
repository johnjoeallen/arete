---
id: COMPAT002
category: Compatibility
matcher: compatibility
scope: schema-property
parameters: { change: property-removed }
---

# COMPAT002 — Existing field is removed

Removing an existing field can break consumers that depend upon it. Requires a baseline specification.

## Detection and scope

The rule has `schema-property` scope and uses the `compatibility` rule:

```yaml
parameters: { change: property-removed }
```

It is intended to compare schema properties in a current document with a
baseline. The current rule deliberately returns no diagnostics without
that comparison input, so no finding is produced from a single current
specification.

## Review guidance

If a baseline contains:

```yaml
properties:
  email: { type: string }
```

removing `email` from a proposed contract may break deserialisation, generated
models, or client logic. Assess deprecation and a migration path first.

## Configuration and limitations

`change: property-removed` selects the future comparison category. The
rule currently receives no baseline, property diff, runtime data, or client
inventory. References and missing comparison input produce no inferred
diagnostic.
