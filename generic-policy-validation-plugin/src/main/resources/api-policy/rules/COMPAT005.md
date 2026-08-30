---
id: COMPAT005
category: Compatibility
matcher: compatibility
scope: schema-property
parameters: { change: enum-value-removed }
---

# COMPAT005 — Existing enum value is removed

Consumers may depend on existing enum values. Removing one is potentially incompatible. Requires a baseline specification.

## Detection and scope

The rule has `schema-property` scope and uses the `compatibility` rule
with:

```yaml
parameters: { change: enum-value-removed }
```

This change type is intended to compare an enum property with the same
property in a baseline specification. The current rule receives no
baseline and deliberately returns no automated diagnostics. Running the rule
against one current document therefore does not report a finding.

## Review guidance

For example, a baseline may contain:

```yaml
properties:
  status:
    type: string
    enum: [PENDING, PAID, CANCELLED]
```

Removing `CANCELLED` from a proposed version can break clients that still send
or expect it. Assess whether the value is truly retired, whether a deprecation
period is required, and whether the change needs a new compatibility version.

## Configuration and limitations

`change: enum-value-removed` selects the future comparison category. No
baseline path, enum diff, runtime payload, client inventory, or compatibility
policy is currently supplied to the rule. References and missing
comparison input produce no evidence. This rule is a placeholder for
baseline-aware analysis, not an active current-document enum validator.
