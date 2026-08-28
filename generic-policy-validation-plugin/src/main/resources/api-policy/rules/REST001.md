---
id: REST001
category: Resource design
detector: resource-path
scope: path
parameters:
  match: operation-verb
---

# REST001 — Resource path contains an operation verb

Resource paths should identify resources rather than describe operations.
Standard HTTP methods should express the operation being performed.

## Poor

`GET /getCustomers`

`POST /createCustomer`

`DELETE /deleteCustomer/123`

## Better

`GET /customers`

`POST /customers`

`DELETE /customers/123`
