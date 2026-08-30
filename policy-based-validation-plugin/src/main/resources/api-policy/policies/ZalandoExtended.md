---
id: Zalando Extended
rules:
  DOC006: 0.5
  DOC007: 0.5
  DOC008: 0.5
  CASE001: 0.5
  CASE003: 0.5
  CASE004: 0.5
  CASE005: 0.5
  JSON003: 0.5
  JSON004: 0.5
  JSON009: 0.5
  JSON010: 0.5
  JSON011: 0.5
  JSON012: 0.5
  JSON013: 0.5
  JSON014: 0.5
  JSON015: 0.5
  JSON016: 0.5
  REST002: 0.5
  REST003: 0.5
  REST004: 0.5
  VERSION001: 0.5
  STATUS001: 0.5
  STATUS002: 0.5
  STATUS003: 0.5
  STATUS004: 0.5
  STATUS005: 0.5
  STATUS006: 0.5
  STANDARD001: 0.5
  STANDARD002: 0.5
  STANDARD003: 0.5
  STANDARD004: 0.5
  STANDARD005: 0.5
  STANDARD006: 0.5
  STANDARD007: 0.5
  STANDARD008:
    points: 0.5
    parameters:
      allowed: X-Request-Id,X-Correlation-Id
  STANDARD009: 0.5
  SECURITY001: 0.5
  SECURITY002:
    points: 0.5
    parameters:
      scopes: read
  STATUS007: 0.5
  STANDARD010: 0.5
  STANDARD012: 0.5
  STANDARD013: 0.5
  STANDARD014: 0.5
  STANDARD023: 0.5
  STANDARD024: 0.5
  CASE008: 0.5
  DOC011: 0.5
  DOC017: 0.5
  DOC018: 0.5
  JSON021: 0.5
  JSON022: 0.5
  REST007: 0.5
---

# Zalando Extended Policy

Everything in the `Zalando` policy, plus the checks that were previously only
in Zalando's supplementary linter rule pack, reworked as Areté rules.

The additions are:

- request-hygiene and contract-completeness checks — path parameters must be
  `required` and typed (`STANDARD012`–`STANDARD014`), the server URL must be a
  well-formed absolute URL (`STANDARD023`), and unreferenced component schemas
  are flagged (`STANDARD024`);
- schema-bound checks — numeric properties need a `minimum` and `maximum`
  (`JSON021`) and string properties need a `maxLength` (`JSON022`), alongside
  the existing enum-casing check (`JSON015`);
- tag hygiene — every operation is tagged (`DOC011`), tag names follow a
  convention (`CASE008`), and top-level tags are declared and described
  (`DOC017`, `DOC018`);
- structural advice — a shared path prefix belongs in the server URL
  (`REST007`).
