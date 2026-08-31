# Areté Policy Engine

The **Areté Policy Engine** (`policy-based-validation-plugin`, plugin id
`generic-policy`) is the built-in, policy-driven validation plugin. Instead of
hard-coding checks in Java, it ships a **policy bundle**: a tree of Markdown +
YAML files defining **matchers** (Distill programs that inspect the normalised
API model and return occurrences), **rules** (named checks that specify a
matcher, scope, and parameters), and **policies** (which rules are active and
what disposition each match has, such as a point deduction or `PROHIBITED`).
Matchers, rule descriptions, and policy definitions can be added or changed
as text files without changing the host application code.

## How it works

On first use the plugin loads its **policy bundle** from the classpath
(`api-policy/` inside the jar) and validates every file in it. `getRuleSets()`
then returns one entry per policy in the bundle — these appear in the
Areté UI as selectable rule sets.

When validation runs, the plugin resolves the requested policy. If the name is
unknown, the **first policy declared** in the bundle manifest is used as the
default. Each rule listed in that policy is then evaluated **in declaration
order**: its matcher runs against the normalised API model and the rule's
`{id, scope, parameters}`, returning zero or more occurrences. If a rule
returns occurrences, the policy's **disposition** (a point deduction or
`PROHIBITED`) is applied once, and one finding is emitted for each occurrence.

### Matcher language

Matchers are written in [Distill](distill.md) (`Matcher.dsl`), currently the
only supported matcher language. It is a small expression language shaped for
rule pipelines (`.map` / `.filter` / `.expand`, slashy regex literals,
`occurrence(...)`).

- **Safe by construction** — the interpreter exposes only the immutable `api`
  and `rule` values, a fixed builtin set, and RE2/J regex. No filesystem,
  network, reflection, or unbounded loops.
- See the [Distill reference](distill.md) for the full grammar and builtin catalogue.

The build also runs optional `Matcher.groovy` counterparts as parity checks;
they are not part of the deployed matcher runtime. See
[The case for Distill](performance.md) for how the engines compare.

---

## The policy bundle

Everything lives under `policy-based-validation-plugin/src/main/resources/api-policy/`:

```
api-policy/
├── PolicyBundle.yaml            # manifest: id → file for every matcher, rule, policy
├── matchers/
│   └── <matcher-id>/
│       ├── Matcher.md          # descriptor (YAML front matter) + prose
│       ├── Matcher.dsl         # the matcher, in Distill — the only runtime used
│       └── Matcher.groovy      # build-time parity check (optional)
├── rules/
│   └── <RULE-ID>.md             # rule front matter + human documentation
└── policies/
    └── <Policy Name>.md         # policy front matter + prose
```

### `PolicyBundle.yaml`

```yaml
formatVersion: 1                 # must be 1
bundleId: arete-policy-bundle
bundleVersion: 0.1.0

rules:
  CASE001: rules/CASE001.md      # manifest key must equal the file's `id`
  REST002: rules/REST002.md
policies:
  "Enterprise Grade": policies/EnterpriseGrade.md
  Zalando: policies/Zalando.md
matchers:
  naming: matchers/naming/Matcher.md
```

`rules`, `policies`, and `matchers` must each be non-empty. Every referenced
path is relative, and `..`, absolute paths, and backslashes are rejected.

Each matcher/rule/policy file is Markdown with a **YAML front matter block**
delimited by `---` lines; the body after the closing `---` is human-readable
documentation.

---

## Matchers

A matcher is a reusable, parameterised Distill program. It reports **what it
observed** by returning occurrences and takes no position on severity or score
— that is the job of the rule and policy.

### Descriptor (`Matcher.md` front matter)

```yaml
---
id: naming                       # must match the manifest key
language: distill                # the matcher language
source: Matcher.dsl              # the matcher source
scopes:                          # the scope values a matcher may request
  - property
  - path-segment
  - query-parameter
parameters:
  convention:
    type: enum                   # enum | string | integer | boolean | list
    required: false
    values: [camelCase, snake_case, kebab-case, hyphenated]  # enum only, non-empty
  suffix:
    type: string                 # string/integer/boolean must NOT declare `values`
    required: false
---
```

Parameter types and their accepted values (checked before the script runs, so
scripts can trust their inputs):

| type      | valid value                                       |
|-----------|---------------------------------------------------|
| `enum`    | a string that is one of `values`                  |
| `string`  | a non-blank string                                |
| `integer` | a whole number                                    |
| `boolean` | `true` / `false`                                  |
| `list`    | a YAML list, or a comma-separated string the loader splits (trimmed, empties dropped) — the matcher always sees a `List<String>` |

### The `api` model

`OpenApiMapAdapter` exposes a stable JSON-shaped model rather than parser
objects. A matcher sees data like this:

```json
{
  "info": {
    "title": "Library API",
    "description": "Books and authors",
    "version": "1.0.0",
    "contactName": null,
    "contactEmail": null,
    "contactUrl": null,
    "licenseName": "Apache-2.0",
    "licenseUrl": "https://www.apache.org/licenses/LICENSE-2.0",
    "openapiVersion": "3.0.0",
    "apiId": null,
    "audience": null,
    "extensionKeys": []
  },
  "servers": ["https://api.example.com/v1"],
  "tags": [{"name": "Books", "description": "Book catalogue operations", "pointer": "/tags/0"}],
  "components": {"securitySchemes": ["bearerAuth"]},
  "security": null,
  "descriptions": [{"pointer": "/info", "text": "Books and authors"}],
  "lint": {"parserMessages": [], "numericStatusKeys": [], "refs": ["#/components/schemas/Book"]},
  "paths": [
    {
      "path": "/books/{bookId}",
      "pointer": "/paths/~1books~1{bookId}",
      "segments": [{"name": "books", "pointer": "/paths/~1books~1{bookId}"}],
      "templateParameters": ["bookId"],
      "operations": ["GET"],
      "operationDetails": [
        {
          "method": "GET",
          "path": "/books/{bookId}",
          "pointer": "/paths/~1books~1{bookId}/get",
          "summary": "Get a book",
          "description": null,
          "operationId": "getBook",
          "tags": ["Books"],
          "extensionKeys": [],
          "security": null,
          "requestBodyPresent": false,
          "requestBodyRequired": false,
          "requestBodyInlineObject": false,
          "mediaTypes": ["application/json"],
          "requestMediaTypes": [],
          "parameters": [
            {
              "name": "bookId",
              "in": "path",
              "pointer": "/paths/~1books~1{bookId}/get/parameters/0",
              "required": true,
              "schemaPresent": true,
              "description": "The book identifier",
              "examplePresent": false,
              "extensionKeys": [],
              "style": null,
              "explode": null,
              "schemaType": "string",
              "schemaMaximum": null
            }
          ],
          "responses": [
            {
              "status": "200",
              "description": "A book",
              "headers": [],
              "headerDetails": [],
              "schemaTypes": ["object"],
              "mediaTypes": ["application/json"],
              "schemaInlineObject": false,
              "exampleStrings": []
            }
          ]
        }
      ]
    }
  ],
  "schemas": [
    {
      "name": "Book",
      "pointer": "/components/schemas/Book",
      "type": "object",
      "array": false,
      "itemsPresent": false,
      "maxItems": null,
      "description": "A book",
      "examplePresent": false,
      "example": null,
      "requiredFields": ["id", "title"],
      "extensionKeys": [],
      "compositionKind": null,
      "inlineCompositionMembers": [],
      "properties": []
    }
  ]
}
```

The example shows the main nesting used by matchers; collections such as
schemas, properties, parameters, responses, and headers expose the additional
fields described by their corresponding objects. `operationDetails[].security`
is `null` unless the operation overrides the global requirement. `api.tags` is
the top-level `tags` list, `api.components.securitySchemes` the declared
security-scheme names, and `api.descriptions` a flat `{pointer, text}` list of
every `description` / `summary` in the document; `api.lint.refs` is every
`$ref` target found in the raw document. Array-typed schemas and properties
carry an `itemsPresent` flag. JSON Pointers are pre-escaped and safe to return
verbatim as `pointer`.

`api.operations`, `api.responses`, and `api.schemaProperties` are flat
convenience views: every `operationDetails` entry across all paths, every
response across all operations, and every property across all schemas, in one
list. Each operation carries its `path`, and each response carries its
operation's `method`, `path`, and `pointer`, so a matcher can select and locate
a subject without an outer `api.paths.expand`. These pair with
[`checks(source) { … }`](distill.md#repeatable-filtermap-checks).

### Writing a matcher

Matchers are written in **Distill** (`Matcher.dsl`); its grammar and builtins
have their own chapter — the [Distill reference](distill.md).

Matchers:

- **An occurrence means a violation.** A matcher emits an occurrence *only* for
  a subject that breaks the rule — a bad thing present (trailing period, inline
  `allOf`), a required thing absent (missing summary, no `404`), or a criterion
  failed (summary too short, example outside its own bounds). A compliant
  subject produces **nothing**. A matcher must never emit an occurrence for a
  passing subject, and never with a message that describes compliance
  ("matches the configured rule"). Rules that flag a construct for human review
  (`PATCH is used`, `version appears in the URI`) still follow this: the flagged
  construct *is* the finding, and its absence yields no occurrence.
- `message` is required and must be non-blank; `pointer` and `path` are
  optional strings.
- Return an empty list when nothing matches — **never** return a score or severity.
- More than 1000 occurrences is a matcher error.
- Any error (raised, step-cap exceeded, wrong return shape) becomes a plugin
  error for that rule's run — it does not abort the other rules.
- The script is compiled when the bundle loads; a compile failure fails the
  whole bundle.

**Safe by construction.** `api` and `rule` are deep-immutable. The language
has no `import`, no I/O, no reflection, no recursion, and execution is bounded
by a hard interpreter-step cap. Matchers work with the core
list/string/closure operations plus a small, fixed set of builtins
(`regexFullMatch`, `tokenize`, `pathSegments`, `parseInt`, …), catalogued in
the [Distill reference](distill.md#builtin-functions). Anything outside that
set is a deliberate, reviewed addition to the runtime, not a workaround in the
script.

The `manual` matcher is the deliberate no-op (`distill(api, rule) { return []; }`).
It keeps a rule in the catalogue as a checklist item that cannot be inferred
from an OpenAPI document.

---

## Rules

A rule binds a matcher to a specific scope and parameter set, and carries the
human explanation used for its findings.

```markdown
---
id: CASE001                       # must match the manifest key
category: Naming                  # free-text grouping shown in the UI
matcher: naming                  # matcher id
scope: property                   # must be one of that matcher's `scopes`
parameters: { convention: camelCase, match: non-conforming }
---

# CASE001 — JSON property is not camelCase

JSON property names should use camelCase where required by policy.
```

- The body **must** start with a level-one heading (`# ...`); its text becomes
  the rule title used in findings.
- `{{parameter-name}}` placeholders in the body are interpolated with the
  rule's parameter values when the documentation is served, e.g.
  `Should not exceed {{maximum-depth}} nested levels.`
- `parameters` may be omitted if the rule needs none.
- Parameter names, value types, required-parameter presence, and the
  scope-vs-matcher match are all checked at load time — **unless** the
  matcher named by the rule is not yet in the bundle, in which case the rule
  is still loaded but left unvalidated (this lets the catalogue document rules
  ahead of their matcher).

The rule is invisible until a policy references it.

---

## Policies

A policy is the deployable artifact: the list of active rules and what each one
costs.

```markdown
---
id: Enterprise Grade              # must match the manifest key (quote if it has spaces)
passingScore: 90                  # optional: below this score the policy fails
grades:                           # optional: numeric score → grade, highest band first
  A: 95
  B: 90
  C: 80
  D: 70
scoring: error                    # optional non-numeric gate: blocker | error
rules:
  REST001: 0.5                    # deduct 0.5 points once if this rule matches
  CASE001: 0.5
  SEC001: PROHIBITED              # any match forces the overall score to 0
---

# Enterprise Grade Policy

Prose describing the policy's intent.
```

- Each rule value is either a **number `0`–`100`** (a point deduction) or the
  literal **`PROHIBITED`**.
- `passingScore:` (optional, `0`–`100`) — the minimum overall score the policy
  considers a pass. Reported on every validation and used by the
  [Automation API](../automation-api.md#scoring-level) verdict.
- `grades:` (optional) — a `label → minimum score` map, listed highest
  threshold first. A score at or above a threshold earns that label; below the
  lowest band the grade is `F`. Reported alongside the numeric score.
- `scoring:` (optional) — a non-numeric gate, `blocker` or `error`, for a
  policy that gates on findings rather than a score. `passingScore` wins if
  both are set; with neither, the API gate defaults to `blocker`.
- A deduction is applied **once per rule**, no matter how many diagnostics the
  rule reported.
- Every rule id must exist in the bundle.
- **Declaration order is report order** in the findings table.
- The first policy in `PolicyBundle.yaml` is the fallback when a caller
  requests an unknown rule set.

Bundled policies: `Enterprise Grade` (the default), `Zalando`, and
`Zalando Extended` — see [Policies](policies.md). Every rule they can reference
is in the [Rule Catalogue](rules.md).

### User policies

You can add policies without rebuilding the plugin. On startup the engine also
reads every `*.md` file in **`~/.arete/policies/`** (filename order), parses each
one exactly like a bundled policy — against the same rules and matchers — and
merges them in. They then appear in the validation picker alongside the bundled
policies.

- A user policy can only reference rules that already exist in the bundle; it
  cannot add rules or matchers.
- If a user policy reuses a bundled `id` (e.g. `Enterprise Grade`), the user
  file **wins** — handy for retuning a bundled policy's deductions locally.
- A malformed file fails the whole engine load, the same way a bad bundle does,
  so the error is impossible to miss.
- The directory is overridable with the `policies-dir` plugin config key or the
  `arete.policy.policies-dir` system property.

Two ready-made examples, `Lenient` (every rule, 0.1 each) and `Pedantic` (every
rule, 2.0 each, security rules `PROHIBITED`), make good starting points.

---

## Scoring

```
qualityScore   = max(0, 100 − Σ deductions for matched rules)
effectiveScore = 0 if any PROHIBITED rule matched, else qualityScore
```

The result reports `overallScore` (`effectiveScore`) and
`overallScoreWithoutBlockers` (`qualityScore`). Severity is `ERROR` for a
`PROHIBITED` match and `WARNING` for a deduction; each diagnostic's
`scoreImprovement` is the points recoverable by fixing that rule.

---

## Adding to the bundle

### A new rule (using an existing matcher)

1. Create `rules/<ID>.md` with front matter (`id`, `category`, `matcher`,
   `scope`, `parameters`) and a `# <ID> — <title>` body.
2. Add `<ID>: rules/<ID>.md` to `PolicyBundle.yaml` under `rules:`.
3. Reference `<ID>` from one or more policies with a deduction or `PROHIBITED`.

### A new matcher and rule

1. Create `matchers/<id>/Matcher.md` (descriptor) and
   `matchers/<id>/Matcher.dsl` (the Distill expression).
2. Add `<id>: matchers/<id>/Matcher.md` to `PolicyBundle.yaml` under
   `matchers:`.
3. Add rules that use it.

### A new policy

1. Create `policies/<Name>.md` with `id` and a `rules:` map.
2. Add `<Name>: policies/<Name>.md` to `PolicyBundle.yaml` under `policies:`.

Rule entries may use the numeric or `PROHIBITED` shorthand, or a declaration
when that policy needs different rule parameters:

```yaml
rules:
  STANDARD008:
    points: 0.5
    parameters:
      allowed: X-Request-Id,X-Correlation-Id
```

The policy parameters are merged over the rule defaults for that run. They are
validated against the rule descriptor at bundle load time; unknown or
incorrectly typed overrides fail fast. The shorthand remains equivalent to a
declaration with no overrides.

### Build & install

```bash
mvn -q -pl policy-based-validation-plugin -am package -DskipTests
cp policy-based-validation-plugin/target/policy-based-validation-plugin-*.jar \
   ~/.arete/plugins/
```

`PolicyBasedValidationPluginTest` / `...LoadIT` load the real bundle and will
fail the build on any manifest, front-matter, scope, parameter, or
rule-compile error.

---

## Validation performed at load time

The bundle fails fast (`BundleValidationException`) on:

- `formatVersion` ≠ 1; empty `rules`/`policies`/`matchers`; unknown top-level
  or front-matter fields; unsafe resource paths.
- A manifest key that doesn't match the `id` inside the referenced file.
- A matcher: an uncompilable source, a missing source, an `enum`
  parameter with no `values`, a
  scalar parameter that declares `values`, an unsupported parameter type.
- A rule: a `scope` not in the matcher's `scopes`, an unknown parameter, a
  wrong-typed parameter value, a missing required parameter, a body with no
  `#` heading. (Skipped only when the matcher isn't bundled yet.)
- A policy: a disposition that is neither `0`–`100` nor `PROHIBITED`, or a
  reference to an unknown rule id.
