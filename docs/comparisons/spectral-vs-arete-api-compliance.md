# Spectral and Areté for API compliance

This document compares Spectral and Areté from an **API compliance** perspective,
not as a general product comparison.

For this comparison, Areté is assumed to be available as:

- a central service for portfolio-wide governance;
- Git integration for pull-request and breaking-change checks;
- a command-line interface;
- Maven and Gradle plugins.

These deployment modes are architectural assumptions for this document. They are
not all implemented in the current repository. The current validation design is
described in [Validation](/home/jallen/git/arete/docs/validation/index.md) and
the [Policy Engine](/home/jallen/git/arete/docs/validation/policy-engine.md).

## Executive conclusion

Spectral is primarily a document linter that can enforce API style and quality
rules close to the source file. Areté is better modelled as a compliance system:
it evaluates named rules under named policies, produces findings, and calculates
a score from policy dispositions.

Spectral is the stronger default for developer-local linting and broad JSON/YAML
ecosystem coverage. Areté is the stronger model for organisation-wide API
compliance when compliance means more than “the document has lint findings” —
for example, policy profiles, score thresholds, prohibited conditions, central
reporting, exceptions, ownership, and change history.

The assumed Areté deployment modes would close much of Spectral's workflow
advantage:

```text
IDE / local CLI / Maven / Gradle
                 \\
                  Git and CI  --->  central Areté compliance service
                                      |
                                      +-- policy profile
                                      +-- score and gates
                                      +-- findings and ownership
                                      +-- historical evidence
```

## What API compliance includes

This comparison uses six dimensions:

1. **Specification conformance** — whether the document uses OpenAPI correctly.
2. **API design standards** — naming, paths, methods, responses, pagination,
   errors, security, versioning, and documentation.
3. **Organisational policy** — which rules apply to which API, team, lifecycle,
   or risk tier.
4. **Change compatibility** — whether a proposed change breaks clients.
5. **Enforcement** — whether a violation blocks a commit, build, merge, release,
   or publication.
6. **Evidence and governance** — whether compliance can be measured, explained,
   audited, and tracked over time.

A linter is excellent at the first two dimensions. A compliance system must also
handle the remaining four.

## High-level comparison

| Compliance concern | Spectral | Areté under the assumed deployment model |
|---|---|---|
| OpenAPI linting | Strong built-in OpenAPI rulesets | Strong for rules represented in the policy bundle |
| Other formats | OpenAPI, AsyncAPI, Arazzo, and JSON Schema formats | Extensible through plugins; bundled policy engine targets Swagger/OpenAPI 2 and OpenAPI 3 |
| Custom rules | YAML/JSON rulesets plus JavaScript/TypeScript functions | Markdown/YAML rules plus reusable Distill matchers; Java plugins for a different engine |
| Target selection | JSONPath `given` expressions | Matcher-defined traversal over a normalised API model |
| Rule reuse | Rules reuse functions and inherited rulesets | Matchers are reused by rules; rules are reused by policies |
| Policy profiles | Inheritance, formats, aliases, and overrides | Named policies with active rules, dispositions, and parameter overrides |
| Severity | Rule-level error, warn, info, hint | Fixed four-level model with plugin-defined labels |
| Compliance score | No native weighted score model | Native deductions, prohibited rules, and 0–100 score |
| Breaking changes | Not a primary document-linting concern; normally requires another tool or custom integration | Assumed Git-aware compatibility analysis and pull-request gating |
| Local use | Mature CLI and JavaScript API | Assumed CLI, Maven, and Gradle integrations |
| Central governance | Usually assembled through CI or a platform | Assumed first-class central service and portfolio view |
| Source locations | Strong line/range output | JSON Pointer and finding locations; assumed adapters can translate these to build annotations |
| Runtime isolation | Custom functions are not sandboxed | Distill is restricted, step-capped, and isolated from parser implementation details |
| Auditability | Depends on surrounding CI or platform | Policy, score, finding, and historical evidence can be first-class records |

Spectral's official documentation describes rules as targets selected with
JSONPath and evaluated by functions, with support for formats, inheritance,
aliases, and overrides. Its CLI documents JSON, JUnit, SARIF, GitHub Actions,
GitLab, and other output formats. See the [Spectral ruleset reference](https://github.com/stoplightio/spectral/blob/develop/docs/guides/4-custom-rulesets.md)
and [CLI reference](https://github.com/stoplightio/spectral/blob/develop/docs/guides/2-cli.md).

## 1. Specification conformance

### Spectral

Spectral has a strong built-in OpenAPI baseline. Its official ruleset supports
OpenAPI 2 and 3 and includes structural and semantic quality concerns such as
responses, references, security definitions, operation metadata, and common
documentation requirements.

Its format detection also allows one ruleset to contain rules for OpenAPI,
AsyncAPI, Arazzo, or JSON Schema and activate the appropriate rules for each
document.

### Areté

Areté separates parsing from matcher execution. The bundled policy engine parses
the specification and exposes a stable JSON-shaped model through
`OpenApiMapAdapter`. Matchers do not depend on Swagger parser classes.

This gives Areté a narrower but more controlled contract. It makes policy rules
less vulnerable to parser-library changes and makes plugin isolation practical.
The trade-off is that a fact must first be exposed by the adapter before a
matcher can use it.

From a compliance perspective:

- Spectral provides broader out-of-the-box structural access.
- Areté provides a more governable and stable rule-authoring surface.
- Neither proves runtime API behaviour or business correctness by itself.

## 2. API design standards

Both systems can express rules for naming, paths, operation IDs, tags,
summaries, descriptions, request and response bodies, media types, status codes,
errors, pagination, security, and versioning.

Spectral generally expresses a simple rule as a target plus a function:

```yaml
operation-summary:
  description: Operation summaries must be present.
  severity: warn
  given: $.paths[*][get,post,put,patch,delete]
  then:
    field: summary
    function: truthy
```

Areté expresses the same concern through a matcher, a rule descriptor, and a
policy entry. This is more ceremony for a one-off check, but it permits one
matcher to support multiple named rules and lets policies change activation,
parameters, and cost.

For compliance programmes, that means a reusable matcher can observe an API
fact while separate rules define whether the fact is mandatory, advisory, or
prohibited for a particular policy.

Spectral can approximate this with multiple rulesets, inheritance, and
overrides, but Areté makes the policy layer explicit.

## 3. Policy and applicability

Compliance is rarely one universal checklist. Requirements commonly differ for
public and internal APIs, experimental and production APIs, low-risk and
regulated APIs, or legacy and newly published APIs.

### Spectral

Spectral provides useful composition tools:

- `extends` for inheriting rulesets;
- format-specific activation;
- aliases for reusable JSONPath expressions;
- overrides for files, formats, document sections, and rules;
- severity changes and rule disabling.

This works well when applicability is primarily a property of a repository,
file, document format, or path. [Spectral overrides](https://github.com/stoplightio/spectral/blob/develop/docs/guides/4d-overrides.md)

### Areté

Areté policies are named compliance profiles. A policy lists active rules and
their dispositions. It can also override rule parameters, so one matcher/rule
definition can be calibrated for different governance profiles.

That is a better conceptual fit when the question is:

> Which compliance standard is this API being assessed against?

rather than:

> Which lint rules should run in this directory?

The assumed central service could select a policy using API metadata, repository
ownership, lifecycle stage, or service classification. That selection mechanism
would need to be explicitly designed; it is not implied by the current policy
bundle.

## 4. Scoring and compliance gates

### Spectral

Spectral reports findings with severity. Its CLI can fail a command according to
a configured minimum severity, and its formatters make results easy to feed into
CI.

This is effective for gates such as “fail if any error exists”. It is less
natural for weighted requirements such as:

- the API must score at least 85;
- critical rules have different weights;
- one prohibited condition forces a zero score;
- the team must recover the missing points before release.

Those policies can be built around Spectral, but they are normally external to
the linter.

### Areté

Areté treats scoring as part of validation. A policy can assign deductions to
rules and mark a rule as `PROHIBITED`. Results can include both the overall score
and the points associated with individual findings.

This makes Areté more suitable for scorecards, release-readiness thresholds,
portfolio reporting, prioritised remediation, and governance dashboards.

Severity answers “how serious is this finding?” A score answers “how compliant is
this API under this policy?” Spectral primarily provides the former; Areté is
designed to provide both.

## 5. Breaking-change compliance

Breaking-change detection is separate from static linting. Examples include:

- removing an endpoint or response status;
- removing a parameter;
- making an optional parameter required;
- narrowing an enum;
- making a response property required;
- changing a schema type incompatibly;
- changing security requirements in a client-breaking way.

### Spectral

Spectral evaluates a document. It does not primarily model the relationship
between a proposed document and its previous released contract. A team can add
custom functions or combine Spectral with a dedicated OpenAPI diff tool, but the
baseline workflow is still document linting.

A Spectral pipeline commonly looks like:

```text
new spec ----------------------> Spectral lint
old spec + new spec -----------> separate breaking-change tool
                                      |
                                      +--> CI aggregation
```

### Areté under the assumed model

A central Areté service with Git integration could make compatibility a native
part of API compliance:

```text
pull request
  ↓
identify previous released or default-branch spec
  ↓
compare old and new API models
  ↓
classify changes by compatibility policy
  ↓
publish findings, score impact, and merge status
```

The same policy system could then express that breaking changes are prohibited
for stable APIs, allowed with migration notes for experimental APIs, or subject
to stricter rules for regulated APIs.

This is a significant potential Areté advantage, but it depends on a high-quality
diff engine and explainable compatibility semantics. A poor diff implementation
would create false confidence regardless of which product owns it.

## 6. Enforcement across the delivery lifecycle

| Lifecycle point | Spectral | Areté under the assumed model |
|---|---|---|
| Editor | Strong through VS Code, JetBrains, and Stoplight integrations | Possible through CLI or future editor adapters |
| Local command | Strong `spectral lint` workflow | Assumed Areté CLI |
| Commit hook | Straightforward | Assumed CLI integration |
| Maven build | Usually shell/plugin integration around Spectral | Assumed native Maven plugin with score thresholds |
| Gradle build | Usually shell/plugin integration around Spectral | Assumed native Gradle plugin |
| Pull request | CI status, annotations, SARIF/GitHub output | Assumed status plus findings, score, policy, and breaking-change report |
| Merge gate | Severity threshold or custom aggregation | Policy score, prohibited rules, and compatibility status |
| Release | Requires surrounding release process | Assumed central release assessment and evidence record |
| Portfolio | Usually an external reporting layer | Assumed central service with compliance history |

The assumed Areté integrations would not necessarily make local linting better
than Spectral. Their value would be consistency: the same policy identity, score
semantics, and compatibility result could be used in local builds, pull requests,
releases, and the central service.

## 7. Evidence, audit, and accountability

Static lint output is not the same as compliance evidence. A compliance record
normally needs to identify:

- the API version and source commit;
- the policy and policy version;
- the active rules;
- the API owner;
- findings at assessment time;
- approved exceptions and their expiry;
- the release or merge decision;
- score changes over time;
- whether findings were fixed, waived, or superseded.

Spectral can participate in this model, but these records normally come from CI,
an API catalogue, or a governance platform around it.

A central Areté service could make these records native. The service would still
need explicit designs for authentication, authorisation, tenancy, policy
versioning, waiver expiry, retention, branch identity, release identity, and
rescore behaviour when policies change.

## 8. Rule-authoring safety and trust

Spectral custom functions are JavaScript or TypeScript. This gives rule authors
maximum flexibility, but the official documentation notes that custom function
code is not sandboxed. [Spectral custom functions](https://github.com/stoplightio/spectral/blob/develop/docs/guides/5-custom-functions.md)

Areté's runtime matcher language, Distill, is intentionally constrained. It
operates over a stable model, exposes a fixed builtin set, and uses execution
limits. This is better suited to centrally distributed policy logic.

The trade-off is authoring power:

- Spectral can solve unusual problems with ordinary JavaScript.
- Areté requires a Distill feature, an adapter-model change, or a Java plugin
  for unusual problems.

## 9. False positives and explainability

Both tools use heuristics for many API design concerns. Neither can infer all
business intent from an API description.

Spectral's advantage is local precision: a rule can target a JSONPath and return
a line/range near the problematic value.

Areté's advantage is policy explanation: a finding can be explained as the
result of a named rule under a named policy, with a score impact and documentation.

An effective compliance finding should answer:

1. What is wrong?
2. Where is it wrong?
3. Why does the selected policy care?
4. What is the effect on compliance or release readiness?

Spectral is already strong on the first two. Areté is designed to add the last
two.

## 10. Operational trade-offs

Spectral is simple to adopt: install it with npm, commit a ruleset, run it
locally or in CI, and consume standard formatter output. This favours
decentralised ownership, but can produce rule, version, and severity drift
between repositories.

The assumed Areté central service favours managed governance: policy versions,
portfolio assessments, visible exceptions, score history, and Git-linked
breaking-change decisions can be centrally managed. It also introduces service
availability, authentication, tenancy, policy rollout, and offline-build
questions.

The CLI, Maven plugin, and Gradle plugin should therefore support a deliberate
offline or cached-policy mode. A central-service outage should not prevent an
unrelated local build from running a known policy version.

## Recommended positioning

Areté should position itself as complementary to Spectral rather than as a
drop-in replacement for every Spectral use case.

### Spectral is the better fit for

- broad JSON/YAML linting;
- fast repository-local adoption;
- JavaScript/TypeScript-owned rules;
- editor-first feedback;
- standard CI annotations;
- teams already invested in Spectral rulesets;
- formats outside the bundled Areté OpenAPI model.

### Areté is the better fit for

- centrally governed API compliance;
- selectable organisation-wide policies;
- weighted scorecards;
- prohibited compliance conditions;
- release and merge decisions;
- Git-aware breaking-change assessment;
- auditable policy and exception history;
- one assessment model across desktop, CLI, build plugins, Git, and a central service;
- controlled execution of distributed policy logic.

## Strategic recommendation

If Areté gains the assumed deployment modes, its differentiating proposition
should be:

> Areté turns API lint findings into governed compliance assessments across the
> API lifecycle.

That proposition depends on five capabilities being consistent everywhere:

1. **One policy identity** — local and central validation identify the same policy
   and version.
2. **One rule semantics** — CLI, Maven, Gradle, Git, and service results agree on
   what a rule means.
3. **One compatibility model** — breaking-change classifications are explainable
   and policy-controlled.
4. **One evidence model** — findings, scores, waivers, commits, and releases are
   linkable.
5. **One failure contract** — integrations distinguish invalid input, policy
   violations, prohibited findings, service errors, and tool failures.

Spectral remains the benchmark for ease of adoption and ecosystem reach. Areté
can compete effectively by owning the layer above linting: policy selection,
scoring, lifecycle enforcement, breaking-change governance, and evidence.

## Sources

- [Spectral repository and supported workflows](https://github.com/stoplightio/spectral)
- [Spectral rulesets and formats](https://github.com/stoplightio/spectral/blob/develop/docs/guides/4-custom-rulesets.md)
- [Spectral built-in functions](https://github.com/stoplightio/spectral/blob/develop/docs/reference/functions.md)
- [Spectral custom functions](https://github.com/stoplightio/spectral/blob/develop/docs/guides/5-custom-functions.md)
- [Spectral CLI and output formats](https://github.com/stoplightio/spectral/blob/develop/docs/guides/2-cli.md)
- [Spectral overrides](https://github.com/stoplightio/spectral/blob/develop/docs/guides/4d-overrides.md)
- [Areté validation overview](/home/jallen/git/arete/docs/validation/index.md)
- [Areté Policy Engine](/home/jallen/git/arete/docs/validation/policy-engine.md)
- [Areté Distill reference](/home/jallen/git/arete/docs/validation/distill.md)
- [Writing an Areté plugin](/home/jallen/git/arete/docs/validation/writing-a-plugin.md)
