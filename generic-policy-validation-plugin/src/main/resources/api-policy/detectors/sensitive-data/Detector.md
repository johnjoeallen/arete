---
id: sensitive-data
language: starlark
source: Detector.star
scopes: [property, query-parameter, path-parameter, header]
parameters:
  pattern:
    type: string
    required: true
---

# Sensitive-data detector

Finds names that match a policy-supplied case-insensitive regular expression in
schema properties and operation parameters. It reports names only; it does
not infer whether a running service encrypts, logs, or otherwise protects a
value.
