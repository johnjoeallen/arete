# History

This page records the significant product and implementation decisions that
shaped Areté, in the order they were made. The rest of the documentation
describes the current product without repeating this background.

## Origins

Areté began as **Speculate**, a local-first OpenAPI viewer imported from an
earlier prototype. The first working tool rendered a spec's endpoints grouped
by tag, showed schema and description prose (as Markdown, and later as raw
HTML where authors relied on it), and shipped as a self-contained jar with
build/run scripts, a tag-triggered release workflow, a branded error page, and
a GitHub Pages landing site.

## Project identity

Speculate was renamed to **Areté**, and its repository, Java package
namespace, launchers, documentation, and branding were aligned with that name.
The policy engine's "detectors" were renamed **matchers** in the same pass,
and the `groupBy` collection operator became `group`.

The current tagline is:

> Areté — the pursuit of API excellence.

The visual identity began as a white "A" monogram on a blue rounded-square
badge and was restyled as a twin-peak mountain "A", with raster exports for
browser, Apple touch, and application icons.

## Architecture

The single-purpose viewer was restructured into a **multi-module Maven build**
with a pluggable scoring layer and a file watcher that reloads an open spec
on body-only edits (and lets it reappear if a deleted file returns).

Scoring is delivered through a published SPI,
`arete-scoring-spi` (formerly `speculate-scoring-spi`), whose Java
package was renamed to match its Maven coordinates and which is published to
Maven Central by a manually-triggered workflow rather than on every release.
Plugins are loaded through a **child-first `URLClassLoader`** so a plugin's
dependencies cannot collide with the host's, and the bundled plugin jar ships
unversioned to match the application jar.

A plugin can declare several named check sets, chosen per spec; these were
first called "scoring types" and then renamed **Policy** across the SPI.
Plugin policies are ordered, and the UI submits a policy by position rather
than name. A plugin may also supply its own severity vocabulary and optional
scoring fields (an overall score, and a per-finding score improvement), and a
single spec can run several plugins at once with the results merged into one
view.

The first bundled plugin wrapped **Zally**, Zalando's OpenAPI linter, as the
default validator. It was later replaced wholesale by the policy engine below.

## The scoring user interface

Scoring moved from running automatically when a spec was added to an
**explicit, on-demand action** on the spec view. That action was named
"Refresh", then "Analyse", and is now **Score**; it also backs the
empty-state action that starts scoring. Results are persisted and reloaded
when a spec is reopened, and a redundant re-run is skipped.

The spec view was split into tabs — first Interface / Model (with Schemas,
Request Bodies and Responses as Model sub-tabs), then Explore / Audit — and
JSON Pointer locations are decoded for display. Findings are shown as
per-endpoint severity badges rather than a flat table, a finding is attributed
to **every** endpoint it names rather than only its pointer's, and a "General"
tab collects findings not attributable to any endpoint or schema. A
"Hide compliant" checkbox became a multi-select severity filter driven by the
plugin's own severity vocabulary. Score impact is broken down by severity and
shown as percentages, and a zero or uncomputed impact is hidden.

## From Zally rules to the policy engine

The bundled validator became a **generic policy engine**
(`arete-policy-plugin`, entry point `PolicyScoringPlugin`).
Instead of hard-coded Java checks it ships a **policy bundle**: a tree of
Markdown + YAML files defining matchers (programs that inspect the normalised
API model and return occurrences), rules (a matcher plus a scope and
parameters), and policies (which rules are active and what disposition each
match carries). Rules, their prose, and policies are text files that can be
changed without touching host code. Rule capabilities and per-policy parameter
overrides let one matcher back many rules.

Rule coverage was built up in waves: the resource-path, operation, naming,
text-style and schema checks first; then HTTP-semantics, bulk, update,
versioning and compatibility rules; then a large pack derived from a
**Mastercard** API style guide (~24 rules, including two deliberately
conflicting ones kept in separate policies); then **Zalando** policy sets.
The `Strict` policy was removed and **`Enterprise Grade`** became the default.
Every bundled rule carries a full Markdown explanation, and the docs site
generates its rule and policy pages from the bundle rather than maintaining a
separate catalogue.

The `Zalando Extended` policy shipped first as a placeholder identical to
`Zalando`. Its extra checks — drawn from Zalando's supplementary linter rule
pack — were then implemented as Areté rules under Areté's own identifiers,
covering path-parameter hygiene, numeric and string schema bounds, tag naming
and documentation, unreferenced component schemas, and shared path prefixes.
The pack's HATEOAS/hypermedia items were left out as not statically checkable,
and its "at most one body parameter" item does not apply once a spec is
normalised to OpenAPI 3.

A further pass drew from **Spectral's** OpenAPI ruleset, adding the
non-overlapping validity and safety checks: arrays must declare `items`,
`security` requirements must name a defined scheme, `description` prose must
not carry active markup, path keys must not contain a query string,
parameters must be unique, server URLs must be well-formed, tags must be
unique, and the API must state a license. A candidate rule for wrong-typed
property examples was dropped because the parser coerces or discards
mismatched example values before a matcher can see them.

## The matcher language

Matchers were first written in **Groovy**, executed in-process. Because Groovy
runs arbitrary code with full JVM access, that was never acceptable as the
long-term runtime for bundle-supplied — and eventually remotely-supplied —
rule logic.

The first replacement was **Starlark** (with RE2/J regular expressions), a
sandboxed language with no I/O, reflection or unbounded loops. All the
detectors were ported to Starlark with a Groovy parity test, and the engine
defaulted to it; Groovy was reframed over several iterations from "deprecated"
to "disabled pending sandbox" to an explicit opt-in, with a configurable
language-precedence list. An optional mode that ran each rule in a disposable
child JVM was added as a second containment option.

Starlark was then itself replaced by **Distill**. Distill is not a
general-purpose language — even Starlark, for all its restrictions, was more
than the job needed. A Distill matcher is a **single expression** in a fixed
frame: given `api` and `rule`, walk the model, keep what matches, and return a
list of `occurrence(...)` values. There are no statements, no local variables,
no user-defined functions, and no way to produce any other kind of result.
It is a focused data-pipeline processor — `.map` / `.filter` / `.expand`,
slashy regex literals, a fixed builtin set — and nothing else.

It was prototyped as "DetectorScript", renamed **Sift** (`.sift`), and finally
**Distill** (`Matcher.dsl`). It grew in phases — Groovy-style `~/regex/`
literals with `==~` / `=~`, bare `/regex/` in operand position, `[key]`
indexing, numeric operators, short-circuit `&&` / `||`, value-based numeric
equality, and `distinct` / `urlHost` / `join` / `group` / `type` / `blank`
builtins — with every detector ported under a Groovy parity test at each step.
Its result model uses `occurrence(...)` rather than a separate diagnostic
abstraction, reflecting that a matcher reports observed rule occurrences.

Once Distill reached parity it became the primary language, and **Starlark
was removed entirely**. Groovy was then withdrawn from the runtime as well: a
deployed Areté loads only `Matcher.dsl` sources, and where a `Matcher.groovy`
still exists it is exercised solely by the build-time parity check, never
against a submitted spec. The child-JVM mode was removed with it — that
isolation only ever existed to contain unsandboxed Groovy, and Distill needs
none. Parity coverage was broadened to a full sweep of every dual-implemented
matcher across each scope and fixture spec, alongside the curated cases, and a
hand-written Java baseline was added to the comparison to quantify the
interpreter's cost.

A matcher is still code — a Distill program — but it is code with only one
shape available to it: consume the immutable `api` and `rule` inputs, and
return occurrences. The interpreter offers no I/O, reflection, recursion, or
unbounded loops, and the grammar offers no way to express anything but the
pipeline. That narrowness, rather than compiled Java in the mould of Zally, is
the deliberate choice. It means a new matcher needs no new release and can
load from outside the application jar — today from the `~/.arete/policies/`
directory, and in future from a remote source pulled and run without trusting
its author with the host JVM. Plausible sources include an organisation's
internal artifact repository (Nexus, Artifactory), a Git repository or
release asset (GitHub, GitLab, an internal host), a plain HTTPS URL or object
store (S3, GCS), an OCI registry alongside container images, or a shared
"policy service" that a team's Areté instances subscribe to. Because a matcher
can only inspect the spec and hand back occurrences, the trust needed to run
one is the trust that its *rules* are sensible — not that its code is safe.
See [The case for Distill](scoring/performance.md) for the measured
trade-off.

Several bundled matchers apply a handful of unrelated checks to one collection
— an operation summary, a schema property — selected per rule by a parameter.
Written as one nested `filter`/`map` this became an inverted
`!(passA || passB || …)` predicate paired with a separate, parallel message
cascade: the condition that triggered an occurrence and the text describing it
were never next to each other. A `checks(source) { … }` block was added to
address this without widening the language: it binds the source collection
once, then takes a list of bare `filter { } .map { }` stanzas — one check per
stanza, condition directly above message — whose occurrences concatenate. It
introduces no statements; a closure parameter became optional (the item is
`it`), and flat `api.operations` / `api.responses` / `api.schemaProperties`
views were added so a stanza rarely needs an outer `expand` to reach its
subject. The list-length builtin `size(list)` was renamed `count(list)` to
match the `xs.count { }` sequence form. `text-style`, `schema`, and
`media-type` were migrated first, gated by the Groovy parity suite.

The same pass audited every matcher against one rule: **an occurrence is a
violation** — a matcher emits one only for a subject that breaks the rule, and
a compliant subject yields nothing. A few matchers carried parameter modes
(`operation` `summary: present`, `schema` `max-items: present`, `text-style`
with no parameters) that were declared but unused and, had a policy them,
would have flagged compliant subjects with a "matches the configured rule"
message; those modes were removed from the matchers and their descriptors.
`schema` findings that had fallen through to "Property matches the configured
schema rule" (`enum-type`, `enum-case`, `extensible`) were given messages that
name the violation.

String and regex literals then gained `{{ expr }}` interpolation — a hole is a
full Distill expression spliced in at evaluation time, with quotes and `/`
inside it no longer treated as the enclosing delimiter — for the cases that
genuinely build part of a pattern or message from a rule parameter.

For the common "is this one of a configured set" case a `list` parameter type
was added, and the string predicates learned to take a list (true if any
element matches): `s.startsWith` / `s.endsWith` / `s.contains` are Java's, and
`s.startsWithWord` / `s.endsWithWord` are the word-aware pair — `t` must land on
a word boundary, so `"Listing"` does not start with the word `"List"`. The
loader turns a comma string or YAML list into a `List<String>` once.
`text-style`'s `non-action-oriented` check moved from a hardcoded verb regex to
`summary.trim().startsWithWord(action-prefixes)`, with `DOC009` supplying the
list — the first of several matchers whose baked-in policy vocabulary should
become rule parameters (issues #144–#154).

## Runtime execution model

Distill matcher sources are parsed once when the bundle loads and the compiled
programs are reused for every scoring, rather than reparsed and discarded
per rule. The interpreter was then reworked to remove avoidable work in the
evaluation loop: compiled regular expressions are cached rather than
recompiled per match, closure parameters are bound through a layered scope
instead of copying the environment for every iterated element, and sequence
operations iterate their input directly instead of materialising it and
opening a stream. The Groovy parity tests gated each step.

User policies load from outside the jar — every `*.md` file in
`~/.arete/policies/` (or a configured directory) is merged into the bundle,
so a deployment can add or override policies without a rebuild.

Large documents are handled explicitly: the OpenAPI parser and the bundle's
YAML loader both accept configurable size limits so that legitimately large
specifications are not rejected as though malformed.

## Automation API

An unauthenticated JSON API under `/api/v1` was added for CI: submit a spec
inline or by URL, name the validator/policy combinations to run, and get
findings plus a pass/fail verdict (JSON or SARIF). Specs are grouped by a
plain **namespace** slug and attributed to a **submitter** — both
self-asserted, neither checked; uniqueness moved from a global spec title to
`(namespace, title)`. Because there is no authentication, the deployment is
required to sit behind a protected boundary. Each policy can declare a
passing score and grade bands (`passingScore:` / `grades:`, or a non-numeric
`scoring: blocker | error`); the score, grade, and pass/fail against that bar
are reported on every scoring, and the automation-API verdict honours the
bar unless the caller overrides it. Grading was later made universal — a
policy with no `grades:` block derives bands from its `passingScore`, or falls
back to a default `A ≥ 90 … D ≥ 60` — and a `+`/`-` is appended for the top or
bottom third of a band. A `shared` deployment mode
locks down the local-filesystem features (path loading, the drop folder,
`file:` URLs) that only make sense for a single-user install, and the
server-side URL fetcher is restricted to `http`/`https` with a mandatory SSRF
guard.

## Tooling and documentation

The documentation moved to a **MkDocs (Material) site** deployed to GitHub
Pages, with `mike` for versioned docs, generated rule and policy pages, and
regenerated UI screenshots. A **matcher test workbench** in the UI lets an
author run a single matcher against a spec and see its occurrences directly.
This history page itself was added once the project's shape had stabilised.
