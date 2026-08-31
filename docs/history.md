# History

This page records the significant product and implementation decisions that
shaped Areté. The rest of the documentation describes the current product
without repeating this background.

## Project identity

Areté began as a local-first API exploration tool under the name Speculate.
The project was renamed to Areté and its repository, package namespace,
launchers, documentation, and branding were aligned with that name.

The current tagline is:

> Areté — the pursuit of API excellence.

The visual identity is a white “A” monogram on a blue rounded-square badge,
with raster exports for browser, Apple touch, and application icons.

## Validation engine

Validation began as a policy-driven plugin architecture. The bundled plugin
was subsequently renamed from the generic policy-validation name to
`policy-based-validation-plugin`, and its Java entry point was renamed to
`PolicyBasedValidationPlugin`.

The policy engine’s declarative matcher language is Distill. Its result model
uses `occurrence(...)`, reflecting that Distill reports observed rule
occurrences rather than a separate diagnostic abstraction. The collection
operator is `group`.

The former Starlark matcher runtime and its resources were removed. Groovy
matcher scripts are retained as an implementation comparison for the Distill
scripts, and the build includes parity tests to detect differences between
the two implementations.

Groovy was then withdrawn from the runtime entirely. A deployed Areté loads
only `Matcher.dsl` sources; where a `Matcher.groovy` still exists it is
exercised solely by the build-time parity check, never against a submitted
spec. The opt-in mode that ran each rule in a disposable child JVM was
removed with it — that isolation existed only to contain unsandboxed Groovy
running in-process, and Distill needs no such containment. Parity coverage
was broadened at the same time to a full sweep of every dual-implemented
matcher across each scope and fixture spec, alongside the curated cases.

Distill matcher sources are parsed once when the bundle loads and the
compiled programs are reused for every validation, rather than reparsed and
discarded per rule. The interpreter was subsequently reworked to remove
avoidable work in the evaluation loop: compiled regular expressions are
cached rather than recompiled per match, closure parameters are bound
through a layered scope instead of copying the environment for every
iterated element, and sequence operations iterate their input directly
instead of materialising it and opening a stream. The Groovy parity tests
gated each step.

Large documents are handled explicitly: the OpenAPI parser and the bundle's
YAML loader both accept configurable size limits so that legitimately large
specifications are not rejected as though malformed.

The `Zalando Extended` policy was a placeholder identical to `Zalando` while
its extra checks were unimplemented. Those checks — drawn from Zalando's
supplementary linter rule pack — were added as Areté rules under Areté's own
identifiers, covering path-parameter hygiene, numeric and string schema
bounds, tag naming and documentation, unreferenced component schemas, and
shared path prefixes. The pack's HATEOAS/hypermedia items were left out as
not statically checkable, and its "at most one body parameter" item does not
apply once a spec is normalised to OpenAPI 3.

A further pass drew from Spectral's OpenAPI ruleset, adding the non-overlapping
validity and safety checks: arrays must declare `items`, `security`
requirements must name a defined scheme, `description` prose must not carry
active markup, path keys must not contain a query string, parameters must be
unique, server URLs must be well-formed, tags must be unique, and the API
must state a license. A candidate rule for wrong-typed property examples was
dropped because the parser coerces or discards mismatched example values
before a matcher can see them.

## User interface

The validation action is named **Score**. It is available from the validation
view and is also used for the empty-state action that starts scoring.

