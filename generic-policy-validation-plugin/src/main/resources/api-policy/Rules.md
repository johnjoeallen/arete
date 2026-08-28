# Speculate starter API rules

This starter bundle deliberately contains one declarative rule. Its detector
will be introduced with the policy engine; this file establishes the bundle's
authoring format without embedding executable logic in Markdown.

## REST001 — Resource path contains an operation verb

```yaml rule
id: REST001
category: Resource design
detector: resource-path
scope: path
parameters:
  match: operation-verb
```

Resource paths should identify resources rather than actions. For example,
prefer `GET /customers` to `GET /getAllCustomers`.

The fenced YAML block is the rule's executable declaration. All other text in
this section is documentation only.
