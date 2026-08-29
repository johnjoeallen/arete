---
id: COMPAT003
category: Compatibility
detector: compatibility
scope: schema-property
parameters: { change: property-renamed }
---

# COMPAT003 — Existing field is renamed

Renaming an existing field is normally equivalent to removing the old field and adding another. Requires a baseline specification.

## Detection and scope

The rule has `schema-property` scope and uses the `compatibility` detector:

```yaml
parameters: { change: property-renamed }
```

The category is intended to compare a schema property with its baseline
counterpart. Since the current detector has no baseline input, it deliberately
returns no automated occurrences; COMPAT003 is not an active current-document
check.

## Review guidance

For example, a baseline may expose `customer_id`:

```yaml
properties:
  customer_id: { type: string }
```

Changing it to `id` can break clients just as removing the old field would.
Consider aliases, a deprecation period, or a compatibility version.

## Configuration and limitations

`change: property-renamed` selects the future comparison category. No property
diff, baseline, reference resolution strategy, runtime payload, or client
inventory is currently available to the detector. Missing comparison input
produces no evidence.
