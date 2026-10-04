# Areté Policy Engine

The **Areté Policy Engine** (`arete-engine`, plugin id
`generic-policy`) is the built-in, policy-driven scoring plugin. Instead of
hard-coding checks in Java, it ships a **policy bundle**: a tree of Markdown +
YAML files defining **matchers** (Distill programs that inspect the normalised
API model and return occurrences), **rules** (named checks that specify a
matcher, scope, and parameters), and **policies** (which rules are active and
what disposition each match has, such as a point deduction or `PROHIBITED`).
Matchers, rule descriptions, and policy definitions can be added or changed
as text files without changing the host application code.

## How it works

On first use the plugin loads its **policy bundle** from the classpath
(`api-policy/` inside the jar) and validates every file in it. `getPolicies()`
then returns one entry per policy in the bundle — these appear in the
Areté UI as selectable policies.

When scoring runs, the plugin resolves the requested policy. If the name is
unknown, the **first policy declared** in the bundle manifest is used as the
default. Each rule listed in that policy is then evaluated **in declaration
order**: its matcher runs against the normalised API model and the rule's
`{id, scope, parameters}`, returning zero or more occurrences. If a rule
returns occurrences, the policy's **disposition** (a point deduction or
`PROHIBITED`) is applied once, and one finding is emitted for each occurrence.

### Matcher language

Matchers are written in [Distill](distill.md) (`Matcher.distill`), currently the
only supported matcher language. It is a small expression language shaped for
rule pipelines (`.map` / `.filter` / `.expand`, slashy regex literals,
`occurrence(...)`).

- **Safe by construction** — the interpreter exposes only the immutable `api`
  and `rule` values, a fixed builtin set, and RE2/J regex. No filesystem,
  network, reflection, or unbounded loops.
- See the [Distill reference](distill.md) for the full grammar and builtin catalogue.

Distill is the only matcher language; there is no second runtime. See
[The case for Distill](performance.md) for why.

---

## The policy bundle

Everything lives under `arete-engine/src/main/resources/api-policy/`:

```
api-policy/
├── PolicyBundle.yaml            # manifest: id → file for every matcher, rule, policy
├── matchers/
│   └── <matcher-id>/
│       ├── Matcher.md          # descriptor (YAML front matter) + prose
│       └── Matcher.distill     # the matcher, in Distill — the only runtime used
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
source: Matcher.distill              # the matcher source
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
| `list`    | a YAML list (passed through as-is), or a comma-separated string the loader splits into a trimmed, empty-dropped `List<String>` — either way the matcher sees a list |

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

**Pointers are stable.** A finding is named by what it is about, not by where it sits in a list, so adding a
parameter, a tag or a response does not make every later finding look new. A parameter is
`.../parameters/<in>:<name>` (a second one with the same `in` and `name`, itself a finding, is
`.../parameters/<in>:<name>#2`); a response is `.../responses/<status>`, and a response header
`.../responses/<status>/headers/<name>`; a tag is `/tags/<name>`; the metadata fields are
`/info/description`, `/info/contact/name` and so on. Two findings of one rule are the same finding in two runs when their
rule, pointer and subject (the rule's own label, such as `GET /orders` or a server's URL) match, whatever the message
says. The one place a pointer still counts position is a member of an inline `allOf`, `anyOf` or `oneOf`, which has no
name (`.../allOf/1/...`). Reports find these pointers' lines in the spec text, so a pointer that names a parameter or tag
still lands on its line.

**`$ref` is transparent.** A same-document `$ref` to a component request body,
response, header, parameter or schema reads like the thing it names, through
chains of references, so a spec that defines something once under `components`
scores the same as one that writes it inline. A property that is a `$ref` carries
the type, format, constraints, enum and description of the schema it names (its
own `description` and `example` win), plus the raw `ref` it was written as. A
cycle, a chain longer than 32 links, or a target that does not exist stops at the
stub and is listed in `api.refProblems` as `{ref, reason}` with reason `cycle`,
`chain-too-long` or `missing`; it is never dropped silently.

**Nested schemas are visible.** A schema's `properties` list holds its direct
properties first, then those below them: properties of inline objects nested
under a property, of `items`, of `additionalProperties` and of inline `allOf` /
`anyOf` / `oneOf` members. Each entry carries its own `pointer` at the place it
is declared and a `depth` counting property hops (composition members, `items`
and `additionalProperties` add none). A referenced schema is not entered; it is
a component and reports at its own definition, once, however many properties
use it. Each schema also carries `nestingDepth`, the longest chain of property
hops below it through `$ref`s (a scalar is 0, an object of scalars 1); a
recursive schema stops where it recurs. `api.schemaProperties` also lists the
properties of schemas written inline in a request body or response, and of
those in component request bodies and responses.

`api.operations`, `api.responses`, and `api.schemaProperties` are flat
convenience views: every `operationDetails` entry across all paths, every
response across all operations, and every property across all schemas, in one
list. Each operation carries its `path`, and each response carries its
operation's `method`, `path`, and `pointer`, so a matcher can select and locate
a subject without an outer `api.paths.expand`. These pair with
[`checks(source) { … }`](distill.md#repeatable-filtermap-checks).

### Writing a matcher

Matchers are written in **Distill** (`Matcher.distill`); its grammar and builtins
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
by a hard interpreter-step cap. A matcher can call only the core
list/string/closure operations and a fixed, closed set of builtins
(`regexFullMatch`, `tokenise`, `pathSegments`, `parseInt`, …), catalogued in
the [Distill reference](distill.md#builtin-functions). That set is the
boundary: widening it takes a reviewed change to the interpreter, and a matcher
has no way to reach around it.

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
  considers a pass. Reported on every scoring and used by the
  [Automation API](../automation-api.md#scoring-level) verdict.
- `grades:` (optional) — a `label → minimum score` map, listed highest
  threshold first. A score at or above a threshold earns that label; within a
  band wide enough to divide, the top third adds a `+` and the bottom third a
  `-`; below the lowest band the grade is `F`. Reported alongside the numeric
  score. **Every policy is graded**: if `grades:` is omitted, bands are derived
  from `passingScore` (`C` = the pass mark, `A`/`B` above, `D` below) when it is
  set, otherwise the default `A ≥ 90, B ≥ 80, C ≥ 70, D ≥ 60` applies.
- `scoring:` (optional) — a non-numeric gate, `blocker` or `error`, for a
  policy that gates on findings rather than a score. `passingScore` wins if
  both are set; with neither, the API gate defaults to `blocker`.
- A deduction is applied **once per rule**, no matter how many diagnostics the
  rule reported.
- Every rule id must exist in the bundle.
- **Declaration order is report order** in the findings table.
- The first policy in `PolicyBundle.yaml` is the fallback when a caller
  requests an unknown policy.

Bundled policies: `Enterprise Grade` (the default), `Zalando`, and
`Zalando Extended` — see [Policies](policies.md). Every rule they can reference
is in the [Rule Catalogue](rules.md).

### User policies

You can add policies without rebuilding the plugin. On startup the engine also
reads every `*.md` file in **`~/.arete/policies/`** (filename order), parses each
one exactly like a bundled policy — against the same rules and matchers — and
merges them in. They then appear in the scoring picker alongside the bundled
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

### How a rule is charged (policy format 2)

By default a rule that matches costs its `points` once, however many times it matches, and a match is the violation. A
policy that declares **`format: 2`** can say more about a rule:

| Setting | Meaning |
|---|---|
| `points: N` | A flat cost, charged once. As before. |
| `per-match: N` and `max: M` | Charge `N` for each match, up to `M` in all. Without `max` the only limit is the score floor of 0. |
| `tiers: { 2: 1, 5: 3, 20: 8 }` | Charge the points of the highest tier the match count reaches: 2 or more costs 1, 5 or more costs 3, 20 or more costs 8. **Below the lowest tier the rule is not violated**: it reports nothing, so a tier map also expresses "up to N is fine". |
| `measure: value` | With `tiers`: tier on the **value** the matcher reports with each occurrence (the deepest schema, the operation with the most parameters), taking the worst one, not on how many occurrences there are. `measure: count` is the default. See below. |
| `expect: match` | The opposite sense: the matcher looks for something that should be there, and **finding nothing** is the violation. It is reported once, at the spec root, with a message that nothing was found. |
| `expect: no-match` | The default, as before. |
| `points: PROHIBITED` | Unchanged: any violation forces the score to 0. Works with `expect: match`. |

Exactly one of `points`, `per-match` and `tiers` is given; `max` goes only with `per-match`; `measure` only with `tiers`. A policy without
`format: 2` that uses the new keys is rejected, so a policy file never changes meaning silently. For example:

```yaml
---
id: Strict Pagination
format: 2
rules:
  PAGE004:
    per-match: 0.5        # each unbounded page size costs half a point...
    max: 3                # ...but never more than 3
  JSON022:
    tiers: { 6: 1, 21: 3 }   # five unbounded strings are tolerated; more cost 1, then 3
---
```

A rule can be charged by what the matcher *measured* rather than how often it matched. A matcher reports a value as
an optional fourth argument of `occurrence(...)`, and the policy opts in with `measure: value`:

```yaml
format: 2
rules:
  JSON025:                         # schema nesting depth; each occurrence carries its depth
    measure: value
    tiers: { 5: 0.5, 7: 1.5 }      # 5 or 6 levels deep costs 0.5, 7 or more costs 1.5
  STANDARD011:                     # parameters per operation; each occurrence carries the count
    measure: value
    tiers: { 9: 1, 13: 3 }
```

The cost follows the **largest** value among the occurrences. Every occurrence is still reported as a finding, with the
value on it. A rule charged by value whose matcher reports no value is a scoring error, not a silent zero. The merge
gate compares the worst value too, so a schema getting deeper fails even when the number of findings is unchanged
(`JSON025 got worse: 5 -> 7`). `measure` goes only with `tiers`.

`expect: match` only makes sense with a matcher written to find evidence of something wanted. Every bundled matcher reports
*violations*, so none of the bundled rules is used that way; it is for your own matchers, for example one that matches an
operation declaring a `Link` header, with `expect: match, points: 2` meaning "somewhere in the API a paginated response must
declare one".

Every finding of a rule scored this way is still listed, and each carries the rule's whole cost. The result also records, per
rule, how many times it matched and what it cost (`rules` in the JSON report), because for a rule scored by count the
**merge-gate** compares the count: it fails when the count rose (or the rule was newly violated), even where the cost did
not move because a cap or a tier absorbed it. A rule charged a flat cost is judged as before: more findings of a rule the
base already violated is existing debt.

`expect: match` is whole-spec today: "at least one match anywhere". Asking that *every* operation or schema match needs a
matcher that reports what it looked at as well as what it found, which is part of the [Distill extensions](../../design-notes/distill-extensions.md)
still to be designed.

### Policy sources

The bundle in the jar is only the default. An engine takes a list of **sources**,
loaded in order and layered: a later source adds to the earlier ones, or replaces an
entry with the same id, and may use any matcher or rule an earlier one defines. An
organisation's own standards live in a bundle of their own, apart from the public tool.

A source is a URI with an optional pin, `<uri>[#sha256=<hex>][&version=<v>]`:

| Source | Example |
|---|---|
| Bundle in the jar | `classpath:api-policy` (the default) |
| Directory or zip on disk | `file:./policy/`, `file:./policy-2.3.1.zip` |
| Zip over HTTPS | `https://host/policy-2.3.1.zip` (plain HTTP only for loopback) |
| Maven coordinate | `maven:org.acme:api-policy:2.3.1` (a zip; add `:jar` for a jar) |

A Maven coordinate is looked up in Maven layout
(`org/acme/api-policy/2.3.1/api-policy-2.3.1.zip`) in the repositories you give the engine
(`mavenRepository(url)`, an `https:` or `file:` base URL) and, if you ask for it, those in your
Maven `settings.xml`. A zip may hold its files at the root or inside one folder.

**Maven settings.** CI already says where artifacts live and who may fetch them in
`settings.xml`, so the engine reads it instead of asking again. `Engine.builder().mavenSettings()`
reads `~/.m2/settings.xml` over `$MAVEN_HOME/conf/settings.xml`; `mavenSettings(path)` reads one
file, as `mvn -s` does; `mavenProfile("id")` activates a profile, as `mvn -P` does. From it the
engine uses:

- the **local repository** (`<localRepository>`, else `~/.m2/repository`), looked at first;
- the **repositories of active profiles** (listed in `<activeProfiles>`, active by default, or
  activated by a system property), then Maven Central;
- **mirrors**, matched by `mirrorOf` (`*`, `external:*`, ids, `!id`) and replacing the repository
  URL, so a corporate Nexus that mirrors everything is used instead of Central;
- **servers**, for credentials: a `<username>`/`<password>` becomes Basic authentication, and
  `<httpHeaders>` are sent as they are (for a bearer token). Credentials are looked up by the id of
  the repository, or of the mirror that replaced it;
- an active **proxy**, with its `nonProxyHosts` and credentials.

`${env.NAME}` and system properties are substituted, so a secret can live in a CI variable. An
encrypted password (`{…}`) is not supported and is refused with a message; use `${env.NAME}`.
A placeholder that is not set is an error when its server is used, never a blank password.

Without the builder, `maven-settings` (`default`, or a path) and `maven-profiles` do the same in
`configure(Map)`. Nothing reads `settings.xml` unless asked.

**Pins.** `sha256` is the digest of the archive; `version` is the bundle's `bundleVersion`.
A source that does not match its pin fails the load, so a bundle cannot change under you.
With `requirePin`, a remote source with no `sha256` is refused before anything is downloaded.
Fetched archives are kept in a cache by digest (`~/.arete/cache/policies` by default), so a
pinned source is read from there on later runs, with no network. Every archive is bounded
in size and entry count.

```java
Engine engine = Engine.builder()
        .policySource("classpath:api-policy")
        .policySource("maven:org.acme:api-policy:2.3.1#sha256=9f2c…")
        .mavenSettings()                       // repositories, mirrors and credentials from settings.xml
        .requirePin(true)
        .build();
```

Without the builder, `new Engine().configure(Map)` reads the same settings from the keys
`policy-sources`, `maven-repositories`, `require-pin`, `cache-dir` and `policies-dir`, or from
system properties `arete.policy.sources`, `arete.policy.maven-repositories`,
`arete.policy.maven-settings`, `arete.policy.maven-profiles`, `arete.policy.require-pin`, `arete.policy.cache-dir` and `arete.policy.policies-dir`.

### Locked rules

A policy can mark a rule `locked`, which stops a team overriding it in `.arete.yaml`:

```yaml
rules:
  SECURITY001:
    points: 5
    locked: true
```

The lock is enforced by the engine. A team that needs different rules has to pick a different policy.

### Team overrides: `.arete.yaml`

A team keeps its deliberate, reviewed deviations from a policy in an `.arete.yaml` next to its
specs. Each states a `reason`, and either disables the rule or changes its `points` or `parameters`:

```yaml
policy: Enterprise Grade
overrides:
  STATUS003:
    reason: Our gateway answers 403 for a missing token, by design.
    disable: true
  PAGE004:
    reason: Reporting endpoints page in thousands.
    points: 1
    parameters: { maximum: 1000 }
```

```java
ScoringResult result = engine.score(input, Overrides.parse(Files.readString(Path.of(".arete.yaml"))));
```

The file is parsed strictly: an unknown key, a missing reason, or an override of a rule that does not
exist is an error, so a typo cannot silently do nothing. An override of a locked rule fails the run. An
override of a rule the chosen policy does not run has no effect. The bundle itself is never changed.

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
   `matchers/<id>/Matcher.distill` (the Distill expression).
2. Add `<id>: matchers/<id>/Matcher.md` to `PolicyBundle.yaml` under
   `matchers:`.
3. Add rules that use it.
4. Regenerate the behaviour snapshots and review the diff:
   `mvn -pl arete-engine test -Dtest=PolicySnapshotTest -Dsnapshot.update=true`.

### Behaviour snapshots

`PolicySnapshotTest` runs every bundled rule against the shared fixture specs
and checks the findings — and each policy's end-to-end score — against golden
files under `src/test/resources/snapshots/`. Any change to a matcher, rule
parameters, or the scoring model surfaces as a diff there. After an intended
change, regenerate with `-Dsnapshot.update=true` and review what moved before
committing the updated snapshots. The per-rule examples in
`src/test/resources/corpus/` and the whole-API specs beside them are checked the same way.

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

### Build

```bash
mvn -q -pl arete-engine -am package -DskipTests
```

The bundle is part of the `arete-engine` jar; the app depends on it, so there
is nothing to install. `EngineTest` and the corpus tests load the
real bundle and will fail the build on any manifest, front-matter, scope,
parameter, or rule-compile error.

---

## Scoring performed at load time

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
