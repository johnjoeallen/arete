# Speculate Policy Engine

The **Speculate Policy Engine** (`generic-policy-validation-plugin`, plugin id
`generic-policy`) is the built-in, policy-driven validation plugin. Instead of
hard-coding checks in Java, it ships a **policy bundle**: a tree of Markdown +
YAML files describing *detectors* (how to find facts in a spec), *rules* (a
named concern built on a detector), and *policies* (which rules are active and
how much each one costs). Anyone can add or change a rule by editing text
files — no host code changes.

- [How it works](#how-it-works)
- [The policy bundle](#the-policy-bundle)
- [Detectors](#detectors)
  - [The `api` model](#the-api-model)
  - [Writing a detector](#writing-a-detector)
- [Rules](#rules)
- [Policies](#policies)
- [Scoring](#scoring)
- [Adding to the bundle](#adding-to-the-bundle)
- [Validation performed at load time](#validation-performed-at-load-time)

---

## How it works

The engine is discovered by the host exactly like any other validation plugin:
it is a shaded jar dropped in `~/.speculate/plugins/`, registered through
`META-INF/services/net.dublinux.speculate.validation.spi.SpecValidationPlugin`,
and loaded via `ServiceLoader` into an isolated classloader. It supports
OpenAPI 3 and Swagger 2 input.

On first use the plugin loads its **policy bundle** from the classpath
(`api-policy/` inside the jar) and validates every file in it. `getRuleSets()`
then returns one entry per policy in the bundle — these appear in the
Speculate UI as selectable rule sets.

For each `validate(spec)` call:

1. The spec is parsed once (swagger-parser) and converted to a small, stable
   `Map` model by `OpenApiMapAdapter`. Detectors never see parser types.
2. The requested policy is resolved (`SpecInput.getRuleSet()`); if the name is
   unknown, the **first policy declared** in the manifest is used as the
   default.
3. Each rule listed in that policy is evaluated **in declaration order**:
   - the rule's detector closure is run against the `api` model plus the
     rule's own `{id, scope, parameters}`;
   - the detector returns zero or more **occurrences** (`{pointer, path,
     message}`);
   - if there is at least one occurrence, the rule's policy **disposition**
     (a point deduction, or `PROHIBITED`) is applied **once**, and one
     `Violation` is emitted per occurrence.
4. A score is computed (see [Scoring](#scoring)) and returned with the
   violations.

Detector scripts are currently **trusted** Groovy from the bundle and run
directly in the plugin JVM (`GroovyShell`). The bundle is part of the build; it
is not a place for untrusted user input.

> **Coming change — sandboxed detectors.** A future release will run detector
> scripts inside a sandbox in the spirit of the Jenkins Groovy sandbox, but
> **stricter**: no filesystem, network, environment, reflection, thread, or
> system-property access, and no classes beyond the whitelisted spec-model and
> collection types. A detector is only ever meant to read the `api` map and the
> `rule` map it is given and return a list of occurrence maps — it needs no
> other capability. Write detectors to that contract now and they will keep
> working unchanged; a script that reaches for anything else will start being
> rejected.

---

## The policy bundle

Everything lives under `generic-policy-validation-plugin/src/main/resources/api-policy/`:

```
api-policy/
├── PolicyBundle.yaml            # manifest: id → file for every rule, policy, detector
├── detectors/
│   └── <detector-id>/
│       ├── Detector.md          # descriptor (YAML front matter) + prose
│       └── Detector.groovy      # the detector closure
├── rules/
│   └── <RULE-ID>.md             # rule front matter + human documentation
└── policies/
    └── <Policy Name>.md         # policy front matter + prose
```

### `PolicyBundle.yaml`

```yaml
formatVersion: 1                 # must be 1
bundleId: speculate-policy-bundle
bundleVersion: 0.1.0

rules:
  CASE001: rules/CASE001.md      # manifest key must equal the file's `id`
  REST002: rules/REST002.md
policies:
  Strict: policies/Strict.md
  "Mastercard Core": policies/MastercardCore.md
detectors:
  naming: detectors/naming/Detector.md
```

`rules`, `policies`, and `detectors` must each be non-empty. Every referenced
path is relative, and `..`, absolute paths, and backslashes are rejected.

Each rule/policy/detector file is Markdown with a **YAML front matter block**
delimited by `---` lines; the body after the closing `---` is human-readable
documentation.

---

## Detectors

A detector is a reusable, parameterised fact-finder. It reports **what it
observed** and takes no position on severity or score — that is the policy's
job.

### Descriptor (`Detector.md` front matter)

```yaml
---
id: naming                       # must match the manifest key
language: groovy                 # only groovy is supported
source: Detector.groovy          # sibling file, resolved next to Detector.md
scopes:                          # the scope values rules may request
  - property
  - path-segment
  - query-parameter
parameters:
  convention:
    type: enum                   # enum | string | integer | boolean
    required: false
    values: [camelCase, snake_case, kebab-case, hyphenated]  # enum only, non-empty
  suffix:
    type: string                 # string/integer/boolean must NOT declare `values`
    required: false
---
```

Parameter types and their accepted values (checked before the script runs, so
scripts can trust their inputs):

| type      | valid rule value                                  |
|-----------|---------------------------------------------------|
| `enum`    | a string that is one of `values`                  |
| `string`  | a non-blank string                                |
| `integer` | a whole number                                    |
| `boolean` | `true` / `false`                                  |

### The `api` model

`OpenApiMapAdapter` exposes only these keys — detectors are independent of the
parser:

```
api.info            { title, description, version, contactName, contactEmail, apiId, audience }
api.servers         [ "https://api.example.com/v1", ... ]
api.paths[]         { path, pointer, segments[], operations[], operationDetails[] }
  .segments[]       { name, pointer }                       # literal segments only, no {params}
  .operationDetails[] { method, pointer, summary, requestBodyPresent,
                        mediaTypes[], parameters[], responses[] }
    .parameters[]   { name, in, pointer }                   # path-item + operation params
    .responses[]    { status, description, headers[], schemaTypes[] }
api.schemas[]       { name, pointer, type, array, maxItems, properties[] }
  .properties[]     { name, pointer, type, array, maxItems, format, nullable,
                      required, enumPresent, enumValues[], extensibleEnum }
```

JSON Pointers are pre-escaped and safe to return verbatim as `pointer`.

### Writing a detector

`Detector.groovy` must **evaluate to a closure** taking two maps and returning
a `Collection` of occurrence maps:

```groovy
{ Map api, Map rule ->
    def params = rule.parameters ?: [:]        // rule = { id, scope, parameters }

    api.schemas
       .collectMany { it.properties ?: [] }
       .findAll { prop -> prop.name.endsWith(params.suffix) }
       .collect { prop ->
           [ pointer: prop.pointer,            // optional, string
             path:    prop.name,               // optional, string (shown as location)
             message: "Property '${prop.name}' has the prohibited suffix" ]  // required, non-blank
       }
}
```

Rules:

- `message` is required and must be non-blank; `pointer` and `path` are
  optional strings.
- Return `[]` when nothing matches — **never** return a score or a severity.
- More than 1000 occurrences is treated as a detector error.
- A thrown exception becomes a plugin error for that run.
- The script is compiled when the bundle loads; a compile failure fails the
  whole bundle.
- **Stay inside the contract.** Operate only on the `api` and `rule` maps and
  plain collections/strings/regex. Do not touch the filesystem, network,
  system properties, environment, threads, or reflection — detectors have no
  need for any of it, and the upcoming sandbox will reject scripts that try.

The `manual` detector is the deliberate no-op: `{ Map api, Map rule -> [] }`.
It keeps a rule in the catalogue as a checklist item that cannot be inferred
from an OpenAPI document.

---

## Rules

A rule binds a detector to a specific scope and parameter set, and carries the
human explanation.

```markdown
---
id: CASE001                       # must match the manifest key
category: Naming                  # free-text grouping shown in the UI
detector: naming                  # detector id
scope: property                   # must be one of that detector's `scopes`
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
- `parameters` may be omitted if the detector needs none.
- Parameter names, value types, required-parameter presence, and the
  scope-vs-detector match are all checked at load time — **unless** the
  detector named by the rule is not yet in the bundle, in which case the rule
  is still loaded but left unvalidated (this lets the catalogue document rules
  ahead of their detector).

The rule is invisible until a policy references it.

---

## Policies

A policy is the deployable artifact: the list of active rules and what each one
costs.

```markdown
---
id: Strict                        # must match the manifest key (quote if it has spaces)
rules:
  REST001: 0.5                    # deduct 0.5 points once if this rule matches
  CASE001: 0.5
  SEC001: PROHIBITED              # any match forces the overall score to 0
---

# Speculate Strict Policy

Prose describing the policy's intent.
```

- Each value is either a **number `0`–`100`** (a point deduction) or the
  literal **`PROHIBITED`**.
- A deduction is applied **once per rule**, no matter how many occurrences the
  detector reported.
- Every rule id must exist in the bundle.
- **Declaration order is report order** in the findings table.
- The first policy in `PolicyBundle.yaml` is the fallback when a caller
  requests an unknown rule set.

Bundled policies: `Strict`, `Mastercard Core`, `Zalando`, `Zalando Extended`.
See [`zalando-rule-catalogue.md`](zalando-rule-catalogue.md) for the
Zalando/Zally rule mapping.

---

## Scoring

```
qualityScore   = max(0, 100 − Σ deductions for matched rules)
effectiveScore = 0 if any PROHIBITED rule matched, else qualityScore
```

The result reports `overallScore` (`effectiveScore`) and
`overallScoreWithoutBlockers` (`qualityScore`). Severity is `ERROR` for a
`PROHIBITED` match and `WARNING` for a deduction; each violation's
`scoreImprovement` is the points recoverable by fixing that rule.

---

## Adding to the bundle

### A new rule (existing detector)

1. Create `rules/<ID>.md` with front matter (`id`, `category`, `detector`,
   `scope`, `parameters`) and a `# <ID> — <title>` body.
2. Add `<ID>: rules/<ID>.md` to `PolicyBundle.yaml` under `rules:`.
3. Reference `<ID>` from one or more policies with a deduction or `PROHIBITED`.

### A new detector

1. Create `detectors/<id>/Detector.md` (descriptor) and
   `detectors/<id>/Detector.groovy` (closure).
2. Add `<id>: detectors/<id>/Detector.md` to `PolicyBundle.yaml` under
   `detectors:`.
3. Add rules that use it.

### A new policy

1. Create `policies/<Name>.md` with `id` and a `rules:` map.
2. Add `<Name>: policies/<Name>.md` to `PolicyBundle.yaml` under `policies:`.

Rule entries may use the numeric or `PROHIBITED` shorthand, or a declaration
when that policy needs different detector parameters:

```yaml
rules:
  STANDARD008:
    points: 0.5
    parameters:
      allowed: X-Request-Id,X-Correlation-Id
```

The policy parameters are merged over the rule defaults for that run. They are
validated against the detector descriptor at bundle load time; unknown or
incorrectly typed overrides fail fast. The shorthand remains equivalent to a
declaration with no overrides.

### Build & install

```bash
mvn -q -pl generic-policy-validation-plugin -am package -DskipTests
cp generic-policy-validation-plugin/target/generic-policy-validation-plugin-*.jar \
   ~/.speculate/plugins/
```

`GenericPolicyValidationPluginTest` / `...LoadIT` load the real bundle and will
fail the build on any manifest, front-matter, scope, parameter, or
Groovy-compile error.

---

## Validation performed at load time

The bundle fails fast (`BundleValidationException`) on:

- `formatVersion` ≠ 1; empty `rules`/`policies`/`detectors`; unknown top-level
  or front-matter fields; unsafe resource paths.
- A manifest key that doesn't match the `id` inside the referenced file.
- A detector: non-`groovy` language, missing/uncompilable source, an `enum`
  parameter with no `values`, a scalar parameter that declares `values`, an
  unsupported parameter type.
- A rule: a `scope` not in the detector's `scopes`, an unknown parameter, a
  wrong-typed parameter value, a missing required parameter, a body with no
  `#` heading. (Skipped only when the detector isn't bundled yet.)
- A policy: a disposition that is neither `0`–`100` nor `PROHIBITED`, or a
  reference to an unknown rule id.
