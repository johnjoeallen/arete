# Automation API — design plan

> **Implemented** in v0.99.78–v0.99.81. The `run` selection and submitter are
> resolved directly in `AutomationApiController` rather than via a
> `HandlerMethodArgumentResolver`; `?httpStatusOnFail=422` is opt-in (the
> default is HTTP 200/201 with the verdict in the body). See
> [`docs/automation-api.md`](../docs/automation-api.md) for the shipped API.

## Goal

Let CI pipelines and scripts submit an OpenAPI spec (inline or by URL), name
the **validator + policy combinations** to run it against, and get the
results back as JSON with a pass/fail verdict — without driving the browser
UI. Each policy suggests the level a spec should clear, and the verdict
honours that unless the caller overrides it.

Every spec is submitted under a **namespace** (which pile it belongs to) by a
**submitter** (who submitted it). Both are **self-asserted and self-trusted** —
plain labels the caller sets, never verified. There is no authentication, so
the API **must sit behind a protected boundary** — see "Trust model".

## What exists today (baseline)

- Spring Boot app, port 6809, local-first, H2 file DB at `~/.arete/data`.
  **No Spring Security, no auth.**
- `specs` table keyed by a **globally unique `title`** (`info.title`).
  `SpecStorageService.saveOrReplace(title, content)` upserts by title — two
  APIs that share a title collide.
- `SpecSource` enum: `PASTED`, `FILE`. No URL source.
- Sidebar and `GET /api/specs` list `specStorageService.findAll()` — a flat
  global list.
- Validation is on-demand: `PluginValidationService.validateMany(rawSpec,
  List<PluginRunRequest>)` → `AggregatedValidationResult`. Results are
  persisted per spec id + content hash in `spec_validation_result`.
- Existing `/api/*` routes (`/api/paste`, `/api/load-file`) return Thymeleaf
  views, not JSON. Only `/api/specs` returns JSON.
- Form POST cap 50MB; `arete.openapi.max-document-size=50MB`.

## The namespace is just a slug

A **namespace** is a slug string on a spec — nothing more. There is **no
namespaces table**: no rows, no creator, no token, no display name. A namespace
"exists" once a spec has been saved under it; the list of namespaces is
`SELECT DISTINCT namespace FROM specs`. The whole feature is one column and a
filter.

- `specs.namespace` — `VARCHAR NOT NULL DEFAULT 'default'`, indexed.
- Slug shape: `^[a-z0-9](?:[a-z0-9-]{0,62})$`, lower-cased and trimmed on
  input. Empty/absent → `default`.
- Nothing to provision and nothing to reserve. Sending specs to a new slug is
  all it takes to create it; it leaves no trace until a spec lands.
- Uniqueness moves from `title` to **`(namespace, title)`** — the same API
  title can exist once per namespace.

New `specs` columns in total: `namespace` and `submitter` (both
`VARCHAR NOT NULL DEFAULT …`, indexed), `source_url` (set when
`source = URL`). `SpecSource` gains `URL`.

## Submitter

The submitter is the self-declared origin of a submission — a pipeline, a
script, a browser session. It sits alongside the namespace: the namespace
groups specs, the submitter attributes them.

- `specs.submitter` — `VARCHAR NOT NULL DEFAULT 'anonymous'`, indexed.
- **Required on every POST, but not authenticated.** Carried by the
  `arete_submitter` cookie (the primary carrier — the browser sends it for
  free, and automation sets `Cookie: arete_submitter=…`); the
  `X-Arete-Submitter` header is accepted as an equivalent for header-only
  clients. A POST with neither is `400`.
- It is not a credential — never checked, no session, no expiry meaning. It is
  a label like the namespace, and just as forgeable.
- Same slug shape as a namespace (`^[a-z0-9](?:[a-z0-9-._]{0,62})$`),
  lower-cased and trimmed; a malformed value is `422`.
- Not a person identity, no email, no `type` discriminator — one flat label,
  like the namespace.

## UI

This is **required scope, not polish**. `specs.namespace` / `specs.submitter`
become `NOT NULL`, and every save path (paste, load-file, drop folder) has to
supply both — the existing flows break otherwise.

- **Sidebar**: a namespace picker — a small `<select>` of the slugs currently
  in use, plus a text input to **type a new slug** and switch to it. Switching
  is instant; the new slug only becomes "real" when a spec is saved under it.
- **Cookies**: `arete_namespace` holds the last-used slug and `arete_submitter`
  the submitter label (both path `/`, `SameSite=Lax`, ~1 year, not `HttpOnly`
  so the sidebar JS can read them). On load the sidebar defaults to the
  cookies' values, falling back to `default` / `ui`.
- The sidebar has a small free-text "submitting as" field that writes
  `arete_submitter`, so a shared browser deployment can still attribute
  submissions; it defaults to `ui`.
- The spec list, `GET /api/specs`, paste, and load-file all operate within the
  current namespace; paste/load-file forms carry `namespace` (and rely on the
  `arete_submitter` cookie) — the same path the API uses.
- **Spec view**: a namespace label and a submitter badge.
- An "All namespaces" option in the picker for a cross-namespace overview
  (read-only listing; new specs still need a concrete slug).

## HTTP API

All under `/api/v1`, JSON in and out. The versioned prefix keeps the HTML
routes untouched.

**Every POST requires** a namespace (in the path) and a submitter (the
`arete_submitter` cookie, or an equivalent `X-Arete-Submitter` header). Neither
is authenticated — see "Submitter" and "Trust model". A POST with no submitter
is `400`.

### Submit a spec (this also validates it)

```
POST /api/v1/namespaces/{namespace}/specs
Cookie: arete_submitter=payments-ci
```

There is no separate validate step. A submission is parsed, stored, and run
through the requested validators in one call; the response carries the
stored-spec resource and one result per validator/policy combination.

**The request must name the combinations to run.** A validator + policy pair
(`generic-policy` / `Enterprise Grade`); for a plugin with no named policies
this is `<validator>/default`. Given as:

- repeatable `?run=<validator>/<policy>` query params (works for any body), or
- a `run` array in a JSON body: `[{ "validator": "generic-policy", "policy": "Enterprise Grade" }]`.

A POST with no combination is `400` — the API never guesses which policies you
meant. (The UI keeps its current picker; only the API is explicit.)

Body, by `Content-Type`:

| Content-Type | body is |
|---|---|
| `application/yaml`, `text/yaml`, `application/x-yaml`, `text/plain` | the raw spec |
| `application/json` **with a top-level `openapi` or `swagger` key** | a raw OpenAPI JSON spec |
| `application/json` **otherwise** | `{ "url": "https://…", "run": [ … ] }` |

Query params shape the response:

- `?failOn=` — `policy` (default) \| `never` \| `error` \| `blocker` \|
  `score<NN`. `policy` means "use each policy's own suggested level" (see
  "Scoring level"); the others force one criterion across every combination.
- `?format=sarif` (or `Accept: application/sarif+json`) → SARIF 2.1.0.
- `?httpStatusOnFail=422` → return `422` instead of `200` on a failing verdict.

Response `200` (`201` if the spec is new):

```json
{
  "spec": {
    "id": 42, "namespace": "payments", "title": "Payments API",
    "source": "URL", "sourceUrl": "https://…/openapi.yaml", "submitter": "payments-ci",
    "updatedAt": "2026-09-01T10:00:00Z",
    "links": { "self": "/api/v1/namespaces/payments/specs/42", "ui": "/spec/42" }
  },
  "ok": false,
  "verdict": "FAIL",
  "results": [
    {
      "validator": "generic-policy",
      "policy": "Enterprise Grade",
      "status": "SUCCESS",
      "score": 87.5,
      "level": { "criterion": "score<90", "source": "policy", "met": false },
      "counts": { "error": 2, "warning": 5, "info": 1, "hint": 0, "blocking": 0 },
      "rulesEvaluated": 155,
      "findings": [
        { "ruleId": "REST001", "severity": "ERROR", "title": "…", "message": "…",
          "pointer": "/paths/~1orders/post", "paths": ["POST /orders"], "documentationUrl": "…" }
      ]
    }
  ]
}
```

`ok` / `verdict` are `FAIL` if **any** combination fails its level. Upsert:
same `(namespace, title)` replaces in place. No `info.title` → `422`.
Re-validating is re-POSTing.

### List / read

```
GET    /api/v1/namespaces                             → [{ slug, specCount }]
GET    /api/v1/namespaces/{namespace}/specs           → [spec summary]; ?submitter= filter
GET    /api/v1/namespaces/{namespace}/specs/{id}      → spec resource
GET    /api/v1/namespaces/{namespace}/specs/{id}/validation → last validation result (?format=sarif)
DELETE /api/v1/namespaces/{namespace}/specs/{id}
```

### Errors

RFC 9457 `application/problem+json` for `4xx`/`5xx` (bad slug, oversize body,
unfetchable URL, unparseable spec, unknown validator, unknown policy).

## Scoring level

A **policy suggests its own pass level** — the bar a spec should clear under
that policy. `Enterprise Grade` might suggest `score<90` (fail below 90);
`Zalando` might suggest `blocker` (fail only on a prohibited rule). The
default `?failOn=policy` honours each combination's suggestion; a caller can
still override with an explicit `?failOn=error` etc. to apply one rule to all.

This needs a small change either side of the SPI:

- **Policy bundle** — an optional `scoring:` key in a policy's `.md` front
  matter, valued in the `failOn` grammar (`blocker` | `error` | `score<NN`).
  Absent → `blocker` (a policy with no opinion fails only on a hard blocker).
  `PolicyBundleLoader.parsePolicy` reads it onto the `Policy` record.
- **SPI** — a new default method
  `default Optional<String> getSuggestedScoreLevel(String ruleSet) { return Optional.empty(); }`.
  The policy plugin returns its parsed `scoring:` value; other plugins inherit
  the empty default and fall back to `blocker`.

The verdict per combination: the requested `failOn` if it isn't `policy`, else
the policy's suggested level, else `blocker`. `ok` is the AND across
combinations.

## Remote spec fetch (URL source)

New `RemoteSpecFetcher` using Spring's `RestClient` (already on the classpath).

- Schemes: `http`, `https` only.
- **SSRF guard** (default on): resolve the host and reject loopback,
  link-local (`169.254/16`, `fe80::/10`), unique-local, private ranges
  (`10/8`, `172.16/12`, `192.168/16`), and the cloud metadata IP
  `169.254.169.254`. Re-check after each redirect. `arete.api.url-fetch.allow-private=true`
  disables it (single-host / dev).
- Redirect limit 5; connect/read timeout 10s (configurable).
- Size cap: reuse `arete.openapi.max-document-size`; stop reading past it.
- Content-Type is advisory — the bytes go through the same `SpecParserService`
  as a paste.
- Store `source = URL`, `source_url = <final URL>`. Re-fetch only on an
  explicit re-submit (no polling in v1 — follow-up).

## Deployment mode

A new setting `arete.deployment.mode = local | shared` (default `local`,
preserving today's behaviour).

- **`local`** — single user on their own machine. Everything as now: the
  `/api/load-file` endpoint reads local paths, the drop folder is watched,
  `file:` sources are fine.
- **`shared`** — multi-user or network-exposed. Local-filesystem reach is a
  cross-user disclosure risk, so:
  - `file:` (and any non-`http(s)`) URL is rejected by `RemoteSpecFetcher` and
    the automation API — `422`, "file URLs are not allowed in shared mode".
  - `POST /api/load-file` returns `403`; the "load from path" UI control is
    hidden.
  - The drop-folder watcher is disabled (or restricted to a single admin
    directory), so one user's specs can't be seeded from another's files.
  - `arete.api.url-fetch.allow-private` is ignored (SSRF guard cannot be
    disabled).

The automation API's URL submission is `http`/`https` only in **both** modes;
`shared` additionally locks down the local-path features above.

## Trust model (write this into the docs)

- **Areté performs no authentication and no authorization on the API.** The
  namespace and submitter are self-asserted labels — as forgeable as an HTTP
  header, because that is all they are.
- **The deployment MUST place the API behind a protected boundary.** A reverse
  proxy that authenticates (OIDC, SSO, mTLS, basic auth), a private network, a
  VPN, or an internal-only ingress — the operator's choice, but something.
  Exposing `/api/v1` directly to an untrusted network is a misconfiguration,
  not a supported mode.
- Given that boundary, namespace and submitter are purely organisational:
  which pile, and who to blame. They are **not isolation** — anyone past the
  boundary can read and write any namespace.
- The URL fetcher is the one attack surface that survives the boundary (an
  authenticated caller pointing it inward); the SSRF guard is mandatory and on
  by default, and cannot be disabled in `shared` mode.
- No per-namespace token in v1. If one is ever needed it is a separate
  `namespace_tokens` table checked as `Authorization: Bearer …` — out of scope
  (see non-goals).

## Schema migration

`spring.jpa.hibernate.ddl-auto=update` adds the new **columns** on its own;
`namespace` and `submitter` pick up their column defaults (`'default'`,
`'anonymous'`) for every existing row — no backfill step needed.

The one thing `update` will not do: **drop the old `title` unique index and add
`(namespace, title)`**. A guarded `ApplicationRunner` on startup handles it
(H2 `ALTER TABLE … DROP CONSTRAINT IF EXISTS` + add composite; idempotent). No
new dependency. Adopt Flyway if/when a second migration appears.

## Implementation phases

1. **Schema** — `specs` columns (`namespace`, `submitter`, `source_url`),
   `SpecSource.URL`, the startup constraint swap.
2. **Namespace + submitter resolution** — slug validation; a
   `HandlerMethodArgumentResolver` reading the `arete_submitter` cookie
   (or `X-Arete-Submitter` header) and `?submitter=` for reads; `400` when a
   POST has no submitter.
3. **Storage** — `SpecStorageService` becomes namespace-aware:
   `saveOrReplace(namespace, submitter, title, content, source, sourceUrl)`;
   repo `findByNamespace…`, `findByNamespaceAndTitle`; `SELECT DISTINCT
   namespace`.
4. **Remote fetch + deployment mode** — `RemoteSpecFetcher` (http/https only)
   + SSRF guard + `arete.deployment.mode`; gate `/api/load-file`, the
   drop-folder watcher, and `allow-private` on `local` mode. Tests for each.
5. **Scoring level** — `scoring:` in policy `.md` front matter →
   `PolicyBundleLoader` / `Policy` record; SPI
   `getSuggestedScoreLevel(String ruleSet)` default method; the policy plugin
   returns its parsed value. A shared `ScoreLevel` parser/evaluator
   (`blocker` | `error` | `score<NN`) used by both the plugin and the API.
6. **API controller** — `AutomationApiController` under `/api/v1`: submit
   (= store + run the requested `run` combinations), list, read, validation,
   delete. Request/response DTOs, result → JSON + SARIF mappers, per-combination
   level evaluation and the overall verdict, problem+json errors, `400` on a
   missing `run` list.
7. **UI** — sidebar namespace picker + new-slug input, "submitting as" field,
   `arete_namespace` / `arete_submitter` cookies, `namespace` on paste/
   load-file, spec-view badges. The picker can show each policy's suggested
   level.
8. **Docs** — `docs/automation-api.md` (+ mkdocs nav), a CI recipe
   (GitHub Actions / GitLab), README mention, the trust-model section
   (**must be behind a protected boundary**); document `scoring:` in the
   policy-authoring docs.
9. **Tests** — WebMvc slice (submit inline, submit URL with a stubbed fetcher,
   required `run` list, `failOn=policy` vs override, SARIF, `400` on missing
   submitter / missing `run`, problem responses); `ScoreLevel` parse/eval;
   policy `scoring:` load; storage-service namespace uniqueness; SSRF-guard
   unit tests; namespace/submitter validation; cookie defaults.

## Explicit non-goals (v1)

- Authentication, user accounts, SSO, per-namespace tokens.
- Scheduled re-fetch of URL specs; webhooks on completion.
- Rate limiting / quotas.
- Any cross-namespace access control.
- Diffing a spec against its previous submission (worth a follow-up).
