# Areté Policy Engine

The **Areté Policy Engine** (`policy-based-validation-plugin`, plugin id
`generic-policy`) is the built-in, policy-driven validation plugin. Instead of
hard-coding checks in Java, it ships a **policy bundle**: a tree of Markdown +
YAML files describing *rules* (how to find facts in a spec), *rules* (a
named concern built on a rule), and *policies* (which rules are active and
how much each one costs). Anyone can add or change a rule by editing text
files — no host code changes.

## How it works

The engine is discovered by the host exactly like any other validation plugin:
it is a shaded jar dropped in `~/.arete/plugins/`, registered through
`META-INF/services/net.dublinux.arete.validation.spi.SpecValidationPlugin`,
and loaded via `ServiceLoader` into an isolated classloader. It supports
OpenAPI 3 and Swagger 2 input.

On first use the plugin loads its **policy bundle** from the classpath
(`api-policy/` inside the jar) and validates every file in it. `getRuleSets()`
then returns one entry per policy in the bundle — these appear in the
Areté UI as selectable rule sets.

For each `validate(spec)` call:

1. The spec is parsed once (swagger-parser) and converted to a small, stable
   `Map` model by `OpenApiMapAdapter`. Rules never see parser types.
2. The requested policy is resolved (`SpecInput.getRuleSet()`); if the name is
   unknown, the **first policy declared** in the manifest is used as the
   default.
3. Each rule listed in that policy is evaluated **in declaration order**:
   - the rule's rule is run against the `api` model plus the rule's own
     `{id, scope, parameters}`;
   - the rule returns zero or more **diagnostics** (`{pointer, path,
     message}`);
   - if there is at least one diagnostic, the rule's policy **disposition**
     (a point deduction, or `PROHIBITED`) is applied **once**, and one
     `Diagnostic` is emitted per diagnostic.
4. A score is computed (see [Scoring](#scoring)) and returned with the
   diagnostics.

### Rule languages

The engine ships Distill and Groovy rule runtimes. A configurable **language
precedence** decides which source is loaded for each rule.

| Language | Source file | Status |
|---|---|---|
| `distill` | `Matcher.dsl` | **Default.** Sandboxed; see the [Distill reference](distill.md). |
| `groovy` | `Matcher.groovy` | Available for validation and execution. |

Where both implementations are provided, the Distill and Groovy sources are
kept in lock-step by the validation tests.

#### Language precedence

For each rule the loader walks the configured precedence list and uses the
**first language whose source file is present**. The default precedence is
`distill,groovy` — Distill first, with Groovy available where no Distill source
exists:

- precedence `distill,groovy` — prefer Distill, then Groovy;
- precedence `groovy,distill` — prefer Groovy, then Distill;
- precedence `groovy` — Groovy only; a rule with no `Matcher.groovy`
  fails the bundle.

Configure it (highest precedence first):

| Mechanism | Value |
|---|---|
| Plugin config key `rule-languages` | comma-separated list, e.g. `distill,groovy` |
| Plugin config key `rule-language` | a single extra language (appended to `distill,groovy`) |
| System property `-Darete.policy.rule-languages` | comma-separated list |
| System property `-Darete.policy.rule-language` | single language |
| Launcher `--rule-languages LIST` | comma-separated list |

=== "Launcher"

    ```bash
    ./arete.sh --rule-languages distill,groovy
    ```

=== "System property"

    ```bash
    java -Darete.policy.rule-languages=distill,groovy -jar arete.jar
    ```

#### Distill (default)

Rules run as [Distill](distill.md) sources — a small expression language shaped
for rule pipelines (`.map` / `.filter` / `.expand`, slashy regex literals,
`occurrence(...)`).

- **Safe by construction** — the interpreter exposes only the immutable `api`
  and `rule` values, a fixed builtin set, and RE2/J regex. No filesystem,
  network, reflection, or unbounded loops.
- See the [Distill reference](distill.md) for the full grammar and builtin catalogue.

#### Groovy

`GroovyMatcherEvaluator` runs a `Matcher.groovy` source directly in the plugin
JVM via a bare `GroovyShell` — **with no sandbox**. It is enabled after
Distill when no Distill source is present.

!!! danger "Groovy rules are unsandboxed"
    A `Matcher.groovy` runs with the full authority of the plugin JVM —
    filesystem, network, process execution, reflection. Use it only with a
    policy bundle you fully trust and control.

---

## The policy bundle

Everything lives under `policy-based-validation-plugin/src/main/resources/api-policy/`:

```
api-policy/
├── PolicyBundle.yaml            # manifest: id → file for every rule, policy, rule
├── rules/
│   └── <rule-id>/
│       ├── Matcher.md          # descriptor (YAML front matter) + prose
│       ├── Matcher.dsl         # the rule — Distill (default runtime)
│       └── Matcher.groovy      # the same rule — Groovy
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
rules:
  naming: matchers/naming/Matcher.md
```

`rules`, `policies`, and `rules` must each be non-empty. Every referenced
path is relative, and `..`, absolute paths, and backslashes are rejected.

Each rule/policy/rule file is Markdown with a **YAML front matter block**
delimited by `---` lines; the body after the closing `---` is human-readable
documentation.

---

## Rules

A rule is a reusable, parameterised fact-finder. It reports **what it
observed** and takes no position on severity or score — that is the policy's
job.

### Descriptor (`Matcher.md` front matter)

```yaml
---
id: naming                       # must match the manifest key
    language: distill                 # the rule language
    source: Matcher.dsl               # the rule source
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

`OpenApiMapAdapter` exposes only these keys — rules are independent of the
parser:

```
api.info      { title, description, version, contactName, contactEmail,
                openapiVersion, apiId, audience, extensionKeys[] }
api.servers   [ "https://api.example.com/v1", ... ]
api.security  [ { <schemeName>: [scopes...] }, ... ]  or null   # global requirement
api.lint      { parserMessages[], numericStatusKeys[] }         # parser + raw-document diagnostics
api.paths[]   { path, pointer, segments[], templateParameters[], operations[], operationDetails[] }
  .segments[]           { name, pointer }                 # literal segments only, no {params}
  .templateParameters[] "customerId"                      # the {names} in the path string
  .operationDetails[]   { method, pointer, summary, description, operationId, tags[],
                          extensionKeys[], security, requestBodyPresent, requestBodyRequired,
                          requestBodyInlineObject, mediaTypes[], requestMediaTypes[],
                          parameters[], responses[] }
    .parameters[]       { name, in, pointer, required, schemaPresent, description,
                          examplePresent, extensionKeys[], style, explode,
                          schemaType, schemaMaximum }
    .responses[]        { status, description, headers[], headerDetails[], schemaTypes[],
                          mediaTypes[], schemaInlineObject, exampleStrings[] }
      .headerDetails[]  { name, schemaPresent }
api.schemas[]  { name, pointer, type, array, maxItems, description, examplePresent,
                 example, requiredFields[], extensionKeys[], compositionKind,
                 inlineCompositionMembers, properties[] }
  .properties[]         { name, pointer, type, array, maxItems, format, description,
                          examplePresent, example, pattern, minLength, maxLength,
                          minimum, maximum, exclusiveMinimum, exclusiveMaximum,
                          extensionKeys[], nullable, required, enumPresent,
                          enumValues[], extensibleEnum }
```

`operationDetails[].security` is `null` unless the operation overrides the
global requirement. JSON Pointers are pre-escaped and safe to return verbatim
as `pointer`.

### Writing a rule

The default runtime is **Distill** (`Matcher.dsl`); its grammar and builtins
have their own chapter — the [Distill reference](distill.md).

Rules:

- `message` is required and must be non-blank; `pointer` and `path` are
  optional strings.
- Return an empty list when nothing matches — **never** return a score or severity.
- More than 1000 occurrences is a rule error.
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

If a rule needs something outside this list, that is a deliberate,
reviewed addition to the runtime — not a workaround in the script.

The `manual` rule is the deliberate no-op (`distill(api, rule) { return []; }`).
It keeps a rule in the catalogue as a checklist item that cannot be inferred
from an OpenAPI document.

---

## Rules

A rule binds a rule to a specific scope and parameter set, and carries the
human explanation.

```markdown
---
id: CASE001                       # must match the manifest key
category: Naming                  # free-text grouping shown in the UI
matcher: naming                  # rule id
scope: property                   # must be one of that rule's `scopes`
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
  scope-vs-rule match are all checked at load time — **unless** the
  rule named by the rule is not yet in the bundle, in which case the rule
  is still loaded but left unvalidated (this lets the catalogue document rules
  ahead of their rule).

The rule is invisible until a policy references it.

---

## Policies

A policy is the deployable artifact: the list of active rules and what each one
costs.

```markdown
---
id: Enterprise Grade              # must match the manifest key (quote if it has spaces)
rules:
  REST001: 0.5                    # deduct 0.5 points once if this rule matches
  CASE001: 0.5
  SEC001: PROHIBITED              # any match forces the overall score to 0
---

# Enterprise Grade Policy

Prose describing the policy's intent.
```

- Each value is either a **number `0`–`100`** (a point deduction) or the
  literal **`PROHIBITED`**.
- A deduction is applied **once per rule**, no matter how many diagnostics the
  rule reported.
- Every rule id must exist in the bundle.
- **Declaration order is report order** in the findings table.
- The first policy in `PolicyBundle.yaml` is the fallback when a caller
  requests an unknown rule set.

Bundled policies: `Enterprise Grade` (the default), `Zalando`, and
`Zalando Extended` — see [Policies](policies.md). Every rule they can reference
is in the [Rule Catalogue](rules.md).

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

### A new rule (existing rule)

1. Create `rules/<ID>.md` with front matter (`id`, `category`, `rule`,
   `scope`, `parameters`) and a `# <ID> — <title>` body.
2. Add `<ID>: rules/<ID>.md` to `PolicyBundle.yaml` under `rules:`.
3. Reference `<ID>` from one or more policies with a deduction or `PROHIBITED`.

### A new rule

1. Create `matchers/<id>/Matcher.md` (descriptor) and
   `matchers/<id>/Matcher.dsl` (the Distill expression).
2. Add `<id>: matchers/<id>/Matcher.md` to `PolicyBundle.yaml` under
   `rules:`.
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

- `formatVersion` ≠ 1; empty `rules`/`policies`/`rules`; unknown top-level
  or front-matter fields; unsafe resource paths.
- A manifest key that doesn't match the `id` inside the referenced file.
- A rule: an uncompilable matcher source, a missing source, an `enum`
  parameter with no `values`, a
  scalar parameter that declares `values`, an unsupported parameter type.
- A rule: a `scope` not in the rule's `scopes`, an unknown parameter, a
  wrong-typed parameter value, a missing required parameter, a body with no
  `#` heading. (Skipped only when the rule isn't bundled yet.)
- A policy: a disposition that is neither `0`–`100` nor `PROHIBITED`, or a
  reference to an unknown rule id.
