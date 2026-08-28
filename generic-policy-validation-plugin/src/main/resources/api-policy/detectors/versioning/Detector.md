---
id: versioning
language: groovy
source: Detector.groovy
scopes: [path, header, media-type, api]
parameters:
  location:
    type: enum
    required: false
    values: [uri, header, media-type]
  match:
    type: enum
    required: false
    values: [present, absent]
---

# Versioning detector

Inspects the stable path, parameter, and media-type facts exposed by the host.
URI versioning is detected from an initial `v` segment; header and media-type
versioning require a version-like name or media type. The unversioned rule is
an API-level absence check. This detector reports contract evidence only.
