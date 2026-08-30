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

