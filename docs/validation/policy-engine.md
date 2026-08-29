# Speculate Policy Engine

The **Speculate Policy Engine** (`generic-policy-validation-plugin`, plugin id
`generic-policy`) is the built-in, policy-driven validation plugin. Instead of
hard-coding checks in Java, it ships a **policy bundle**: a tree of Markdown +
YAML files describing *detectors* (how to find facts in a spec), *rules* (a
named concern built on a detector), and *policies* (which rules are active and
how much each one costs). Anyone can add or change a rule by editing text
files — no host code changes.

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
   - the rule's detector is run against the `api` model plus the rule's own
     `{id, scope, parameters}`;
   - the detector returns zero or more **occurrences** (`{pointer, path,
     message}`);
   - if there is at least one occurrence, the rule's policy **disposition**
     (a point deduction, or `PROHIBITED`) is applied **once**, and one
     `Violation` is emitted per occurrence.
4. A score is computed (see [Scoring](#scoring)) and returned with the
   violations.

### Detector languages

The engine ships two detector runtimes. A detector can be authored in either
language — `Detector.star` and `Detector.groovy` sit side by side under the
detector directory — and a configurable **language precedence** decides which
source is loaded for each detector.

| Language | Source file | Status |
|---|---|---|
| `starlark` | `Detector.star` | **Default.** Always available, sandboxed. |
| `groovy` | `Detector.groovy` | **Disabled by default** — opt-in, unsandboxed. |

#### Language precedence

For each detector the loader walks the configured precedence list and uses the
**first language whose source file is present**. The default precedence is
`starlark` only, so Groovy never runs unless you opt in. Enabling Groovy adds
it to the list:

- precedence `starlark,groovy` (what `--enable-groovy-detectors` sets) — a
  detector keeps running on Starlark wherever a `Detector.star` exists, and
  only falls back to `Detector.groovy` when there is no Starlark source;
- precedence `groovy,starlark` — prefer Groovy where a `Detector.groovy`
  exists, fall back to Starlark otherwise;
- precedence `groovy` — Groovy only; a detector with no `Detector.groovy`
  fails the bundle.

Configure it (highest precedence first):

| Mechanism | Value |
|---|---|
| Plugin config key `detector-languages` | comma-separated list, e.g. `groovy,starlark` |
| Plugin config key `detector-language` | a single extra language (added after `starlark`) |
| System property `-Dspeculate.policy.detector-languages` | comma-separated list |
| System property `-Dspeculate.policy.detector-language` | single language |
| Launcher `--detector-languages LIST` | comma-separated list |
| Launcher `--enable-groovy-detectors` | shorthand for `starlark,groovy` |

=== "Launcher"

    ```bash
    ./speculate.sh --enable-groovy-detectors
    ./speculate.sh --detector-languages groovy,starlark
    ```

=== "System property"

    ```bash
    java -Dspeculate.policy.detector-languages=groovy,starlark -jar speculate.jar
    ```

#### Starlark (default)

Detectors run as [Starlark](https://bazel.build/rules/language) sources.

- **Safe by construction** — a Starlark detector cannot touch the filesystem,
  network, environment, threads, reflection, or any class outside the
  whitelisted value model. It reads the immutable `api` and `rule` values and
  returns a list of occurrence dicts. Nothing to sandbox.
- Regex is [RE2/J](https://github.com/google/re2j) — linear-time, no
  catastrophic backtracking.
- The Starlark detectors were verified against the Groovy implementations
  across a corpus sweep.

#### Groovy (disabled by default)

`GroovyDetectorRuntime` runs a `Detector.groovy` source directly in the plugin
JVM via a bare `GroovyShell` — **with no sandbox**. It is a deliberate, opt-in
fallback and is **disabled by default** until the detector sandbox described in
the
[sandbox plan](https://github.com/johnjoeallen/speculate/blob/main/design-notes/policy-engine-sandbox-plan.md)
lands. It is *not* deprecated — the intent is to re-enable it as a first-class
option once bundle-supplied Groovy can be run safely.

!!! danger "Groovy detectors are unsandboxed"
    A `Detector.groovy` runs with the full authority of the plugin JVM —
    filesystem, network, process execution, reflection. Only add `groovy` to
    the precedence for a policy bundle you fully trust and control. That is why
    it is off by default.

---

## The policy bundle

Everything lives under `generic-policy-validation-plugin/src/main/resources/api-policy/`:

```
api-policy/
├── PolicyBundle.yaml            # manifest: id → file for every rule, policy, detector
├── detectors/
│   └── <detector-id>/
│       ├── Detector.md          # descriptor (YAML front matter) + prose
│       ├── Detector.star        # the detector — Starlark (default runtime)
│       └── Detector.groovy      # the same detector — Groovy (opt-in runtime)
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
  "Enterprise Grade": policies/EnterpriseGrade.md
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
language: starlark                # the detector language
source: Detector.star             # the detector source
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
api.info      { title, description, version, contactName, contactEmail,
                openapiVersion, apiId, audience }
api.servers   [ "https://api.example.com/v1", ... ]
api.security  [ { <schemeName>: [scopes...] }, ... ]  or null   # global requirement
api.paths[]   { path, pointer, segments[], operations[], operationDetails[] }
  .segments[]           { name, pointer }                 # literal segments only, no {params}
  .operationDetails[]   { method, pointer, summary, requestBodyPresent, security,
                          mediaTypes[], requestMediaTypes[], parameters[], responses[] }
    .parameters[]       { name, in, pointer, style, explode, schemaType, schemaMaximum }
    .responses[]        { status, description, headers[], schemaTypes[], mediaTypes[] }
api.schemas[]  { name, pointer, type, array, maxItems, properties[] }
  .properties[]         { name, pointer, type, array, maxItems, format, nullable,
                          required, enumPresent, enumValues[], extensibleEnum }
```

`operationDetails[].security` is `null` unless the operation overrides the
global requirement. JSON Pointers are pre-escaped and safe to return verbatim
as `pointer`.

### Writing a detector (Starlark)

`Detector.star` must define a top-level function `detect(api, rule)` that
returns a list of occurrence dicts:

```python
def detect(api, rule):
    suffix = rule["parameters"]["suffix"]        # rule = {"id", "scope", "parameters"}
    out = []
    for schema in api["schemas"]:
        for prop in schema["properties"]:
            if prop["name"].endswith(suffix):
                out.append({
                    "pointer": prop["pointer"],          # optional, string
                    "path": prop["name"],                # optional, string (shown as location)
                    "message": "Property has the prohibited suffix " + suffix,  # required, non-blank
                })
    return out
```

Rules:

- `message` is required and must be non-blank; `pointer` and `path` are
  optional strings.
- Return `[]` when nothing matches — **never** return a score or a severity.
- More than 1000 occurrences is a detector error.
- Any error (raised, step-cap exceeded, wrong return shape) becomes a plugin
  error for that rule's run — it does not abort the other rules.
- The script is compiled when the bundle loads; a compile failure fails the
  whole bundle.

**Safe by construction.** `api` and `rule` are deep-immutable. The language
has no `import`, no I/O, no reflection, no recursion, and execution is bounded
by a hard interpreter-step cap. The only capabilities beyond core
list/dict/string/`for`/comprehension work are these builtins:

| Builtin | Purpose |
|---|---|
| `re_fullmatch(pattern, text)` | whole-string match (RE2 syntax, linear time) |
| `re_search(pattern, text)` | match anywhere in `text` |
| `tokenize(text, delims)` | split on any char in `delims`, dropping empty tokens |
| `parse_int(text, fallback)` | base-10 int, or `fallback` if not one |
| `url_host(url)` | host component of a URL, or `None` |

If a detector needs something outside this list, that is a deliberate,
reviewed addition to the runtime — not a workaround in the script.

The `manual` detector is the deliberate no-op (`def detect(api, rule): return []`).
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

Bundled policies: `Strict`, `Enterprise Grade`, `Zalando`, `Zalando Extended`.
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
   `detectors/<id>/Detector.star` (the `detect(api, rule)` function).
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
detector-compile error.

---

## Validation performed at load time

The bundle fails fast (`BundleValidationException`) on:

- `formatVersion` ≠ 1; empty `rules`/`policies`/`detectors`; unknown top-level
  or front-matter fields; unsafe resource paths.
- A manifest key that doesn't match the `id` inside the referenced file.
- A detector: an uncompilable `Detector.star`, a missing source, an `enum`
  parameter with no `values`, a
  scalar parameter that declares `values`, an unsupported parameter type.
- A rule: a `scope` not in the detector's `scopes`, an unknown parameter, a
  wrong-typed parameter value, a missing required parameter, a body with no
  `#` heading. (Skipped only when the detector isn't bundled yet.)
- A policy: a disposition that is neither `0`–`100` nor `PROHIBITED`, or a
  reference to an unknown rule id.
