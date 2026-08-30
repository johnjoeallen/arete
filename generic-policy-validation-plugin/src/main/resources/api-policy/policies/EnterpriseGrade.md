---
id: Enterprise Grade
rules:
  REST001: 0.5
  DOC001: 0.5
  DOC002: 0.5
  DOC003: 0.5
  DOC004: 0.5
  DOC005: 0.5
  DOC006: 0.5
  DOC007: 0.5
  DOC008: 0.5
  DOC009: 0.5
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
      allowed: X-Request-Id,X-Correlation-Id,X-Trace-Id
  STANDARD009: 0.5
  SECURITY001: 0.5
  SECURITY002:
    points: 0.5
    parameters:
      scopes: read,write
  CASE001: 0.5
  CASE002: 0.5
  CASE003: 0.5
  CASE004: 0.5
  CASE005: 0.5
  REST002: 0.5
  REST003: 0.5
  REST004: 0.5
  REST005: 0.5
  REST006: 0.5
  JSON003: 0.5
  JSON004: 0.5
  JSON006: 0.5
  JSON007: 0.5
  JSON009: 0.5
  JSON010: 0.5
  JSON011: 0.5
  JSON012: 0.5
  JSON013: 0.5
  JSON014: 0.5
  JSON015: 0.5
  JSON016: 0.5
  HTTP004: 0.5
  HTTP005: 0.5
  HTTP001: 0.5
  HTTP002: 0.5
  HTTP006: 0.5
  HTTP008: 0.5
  UPDATE002: 0.5
  UPDATE001: 0.5
  UPDATE003: 0.5
  BULK001: 0.5
  BULK002: 0.5
  BULK003: 0.5
  VERSION001: 0.5
  VERSION002: 0.5
  VERSION003: 0.5
  VERSION004: 0.5
  COMPAT001: 0.5
  COMPAT002: 0.5
  COMPAT003: 0.5
  COMPAT004: 0.5
  COMPAT005: 0.5
  COMPAT006: 0.5
  STATUS001: 0.5
  STATUS002: 0.5
  STATUS003: 0.5
  STATUS004: 0.5
  STATUS005: 0.5
  STATUS006: 0.5
  STATUS007:
    points: 0.5
    parameters:
      headers: RateLimit-Limit,RateLimit-Remaining
  STANDARD010: 0.5
  STANDARD011: 0.5
  STANDARD012: 0.5
  STANDARD013: 0.5
  STANDARD014: 0.5
  STANDARD015: 0.5
  STANDARD016: 0.5
  STANDARD017: 0.5
  HTTP009: 0.5
  DOC010: 0.5
  DOC011: 0.5
  DOC012: 0.5
  CASE006: 0.5
  JSON017: 0.5
  SEC009: 0.5
  STANDARD018: 0.5
  STANDARD019: 0.5
  STANDARD020: 0.5
  STANDARD021: 0.5
  STANDARD022: 0.5
  DOC013: 0.5
  DOC014: 0.5
  STATUS008: 0.5
  JSON018: 0.5
  JSON019: 0.5
  DOC015: 0.5
  JSON020: 0.5
  DOC016: 0.5
  ERROR011: 0.5
---

# Enterprise Grade Policy

The default policy: it enables every generally-applicable bundled rule, with a
few rule parameters calibrated for a typical enterprise API (allow-listed
proprietary headers, expected OAuth2 scopes, standard rate-limit headers).

Each matched rule deducts half a point once, regardless of how many diagnostics
it reports — a deliberately simple, uniform baseline. Organisations should
publish their own policy with calibrated deductions and `PROHIBITED`
dispositions rather than relying on this one unchanged.
