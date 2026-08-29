---
id: STANDARD022
category: Standards
detector: document-lint
scope: api
parameters: { check: numeric-status-key }
---

# STANDARD022 — HTTP status keys are bare numbers

Response status keys must be quoted strings. A bare `200:` is a YAML integer
key; some tools coerce it, others reject the document.

## Violation

```yaml
responses:
  200: { description: OK }
  404: { description: Not found }
```

## Compliant

```yaml
responses:
  '200': { description: OK }
  '404': { description: Not found }
```

## Detection and scope

The rule has `api` scope and uses the `document-lint` detector with
`check: numeric-status-key`. It scans the raw document for indented lines of
the form `NNN:` where `NNN` is a 1xx–5xx number, and reports once if any are
found.

## Configuration and limitations

The check is a textual heuristic on the raw document. A property literally
named after a three-digit number would be a false positive; quoting all
status keys avoids the warning.
