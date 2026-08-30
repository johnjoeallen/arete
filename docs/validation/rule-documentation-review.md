# Rule documentation review

## Scope

This review covers all 146 rule documents under
`policy-based-validation-plugin/src/main/resources/api-policy/rules/`.

The review assesses documentation, not the desirability of the rule itself. A
rule can be well documented while still being a heuristic, a future capability,
or a policy decision that an organisation may choose not to enable.

The review checked the rule metadata against its matcher, the explanation of
the rule, the detection/scope description, the explanation of parameters and
limitations, and the presence and apparent intent of violation and compliant
examples. Examples were checked as OpenAPI fragments and against the matcher
semantics where that could be established statically. They are not all complete
standalone OpenAPI documents, so a fragment must be placed at the documented
location before it can be parsed as a complete specification.

## Rating scale

| Rating | Meaning |
|---|---|
| **A** | Complete explanation, detection and scope, useful violation example, useful compliant example, and limitations/parameters. The examples appear consistent with the rule. |
| **B** | Substantively useful and mostly correct, but missing one documentation element, usually an explicit intent section, a separately labelled compliant example, or fuller parameter guidance. |
| **C** | Partial documentation. Either the rule is intentionally comparison-mode/no-op and needs special treatment, or it has only one side of the example story or limited detection guidance. |
| **D** | Inadequate for users. Usually only a title, short explanation, and an unlabelled/compliant fragment; the reader cannot reliably tell what violation to create or what a correct specification looks like. |

## Initial review summary

The following was the state before the remediation pass described below.

| Rating | Rules | Count |
|---|---|---:|
| A | Complete | 56 |
| B | Mostly complete | 44 |
| C | Partial or intentionally limited | 11 |
| D | Needs documentation work | 35 |
| **Total** |  | **146** |

The most important initial issue was the 35 D-rated documents. They were not
necessarily wrong implementations, but they did not give a developer enough
information to construct a failing specification, understand the matching
boundary, and then repair it confidently.

## Remediation status

The first remediation pass has now completed for all 35 initially D-rated
documents. Each has explicit detection/scope, a review-candidate example, a
compliant example, and configuration/limitation guidance. The misleading
examples identified below were also corrected.

A follow-up structural scan reports:

- 146 of 146 rule documents contain fenced examples;
- 146 of 146 contain detection/scope guidance;
- 146 of 146 contain both review-candidate and compliant-example guidance;
- 0 documents remain D-rated under the original structural rubric.

The remaining B and C ratings are quality improvements rather than missing
minimum documentation.

## Current rescan

The current rule bundle contains 155 documents. A fresh scan of all 155
documents after the B-rating remediation now reports:

| Rating | Count |
|---|---:|
| A | 155 |
| B | 0 |
| C | 0 |
| D | 0 |
| **Total** | **155** |

All 155 documents now contain the core documentation sections and at least one
fenced example. Additional rules found in the rescan include
`DOC019`, `DOC020`, `JSON023`, `JSON024`, `SECURITY003`, `SECURITY004`, and
`STANDARD025`–`STANDARD028`; all are currently rated A.

The six compatibility rules (`COMPAT001`–`COMPAT006`) now include explicit
baseline/proposed comparison shapes while still stating that the current
implementation does not execute those comparisons. This keeps the examples
useful without presenting future capability as current runtime behaviour.

## Per-rule ratings

### A — complete

These documents explain the concern, describe how it is detected, show the
review-candidate and compliant cases, and document limitations or parameters.

`BULK003`, `CASE001`, `CASE002`, `CASE003`, `CASE004`, `CASE005`, `DOC003`,
`DOC009`, `DOC017`, `DOC018`, `HTTP001`, `HTTP002`, `HTTP003`, `HTTP004`,
`HTTP005`, `HTTP006`, `JSON003`, `JSON004`, `JSON006`, `JSON007`, `JSON009`,
`JSON010`, `JSON011`, `JSON012`, `JSON013`, `JSON014`, `JSON015`, `JSON021`,
`JSON022`, `REST001`, `REST002`, `REST003`, `REST004`, `REST005`, `REST006`,
`REST007`, `STANDARD001`, `STANDARD002`, `STANDARD003`, `STANDARD004`,
`STANDARD005`, `STANDARD006`, `STANDARD007`, `STATUS001`, `STATUS002`,
`STATUS003`, `STATUS004`, `STATUS005`, `STATUS006`, `UPDATE001`, `UPDATE002`,
`UPDATE003`, `VERSION001`, `VERSION002`, `VERSION003`, `VERSION004`.

These are the current documentation exemplars. New rules should follow their
structure, especially the separation between detection, violation example,
compliant example, and limitations.

### B — mostly complete

These documents are usable, but each needs a focused improvement before it is a
model rule document. The common omissions are an explicit intent explanation,
an explicit compliant example, or a fuller explanation of the rule boundary.

`BULK001`, `BULK002`, `CASE006`, `CASE007`, `DOC001`, `DOC002`, `DOC004`,
`DOC005`, `DOC006`, `DOC010`, `DOC011`, `DOC012`, `DOC013`, `DOC014`, `DOC015`,
`DOC016`, `ERROR011`, `HTTP009`, `JSON016`, `JSON017`, `JSON018`, `JSON019`,
`JSON020`, `SEC009`, `SECURITY001`, `SECURITY002`, `STANDARD008`, `STANDARD009`,
`STANDARD010`, `STANDARD011`, `STANDARD012`, `STANDARD013`, `STANDARD014`,
`STANDARD015`, `STANDARD016`, `STANDARD017`, `STANDARD018`, `STANDARD019`,
`STANDARD020`, `STANDARD021`, `STANDARD022`, `STANDARD023`, `STATUS007`,
`STATUS008`.

Recommended treatment:

- Add a short `## Intent` section where the document starts directly with
  detection.
- Label every code sample as either a review candidate/violation or compliant.
- For compact rules, add one sentence describing what the matcher ignores.
- For rules with parameters, state the exact accepted values and comparison
  boundary, not only the configured value used by the bundled rule.

### C — partial or intentionally limited

`COMPAT001`–`COMPAT006` are correctly documented as future comparison-mode
capabilities that cannot produce a finding from one current document. They do
not need a false “compliant current specification” example. They do need a
concrete old/new pair once baseline comparison is implemented.

`HTTP008` is correctly documented as a current no-op. It should remain explicit
about that status and should not be presented as an active compliance check.

The remaining C-rated documents need ordinary completion:

- `CASE008` has a violation example but no separately labelled compliant example.
- `DOC007` and `DOC008` have examples but use an `Examples` heading rather than
  clearly separating the review candidate and compliant specification.
- `STANDARD024` has a good violation example but no compliant example.

## D-rated rules addressed in the first remediation pass

The following documents need substantial documentation improvement:

`CONTENT001`, `CONTENT002`, `CONTENT003`, `CONTENT004`, `ERROR001`, `ERROR002`,
`ERROR003`, `ERROR004`, `ERROR005`, `ERROR006`, `ERROR007`, `ERROR008`,
`ERROR009`, `ERROR010`, `FIELD001`, `FILTER001`, `FILTER002`, `PAGE001`,
`PAGE002`, `PAGE003`, `PAGE004`, `PAGE005`, `PAGE006`, `SEC001`, `SEC002`,
`SEC003`, `SEC004`, `SEC005`, `SEC006`, `SEC007`, `SEC008`, `SORT001`,
`SORT002`, `SORT003`, `SORT004`.

These documents initially contained only front matter, a title, and a short
description. Some included a single code fragment, but it was not labelled and
often showed the correct condition rather than the violation. The first pass
added the following to each one:

1. Intent — why the API practice matters.
2. Detection and scope — exactly which object is inspected and which objects are
   ignored.
3. Review-candidate example — a minimal fragment that definitely produces a
   finding.
4. Compliant example — the smallest repair that does not produce a finding.
5. Parameters, references, and limitations — including case sensitivity,
   inheritance, `$ref` handling, and known false positives where relevant.

## Sample correctness findings

The following samples are not suitable as violation examples. They show the
condition the rule asks for, so they should either be labelled **Compliant
example** or replaced with a deliberately failing fragment.

| Rules | Current sample problem | Required correction |
|---|---|---|
| `ERROR001`–`ERROR003` | The snippets show a success, client-error, or server-error response, which is the condition required by the rule. | Add an operation with the relevant response class absent, then retain the current snippet as the compliant example. |
| `ERROR004` | The sample includes `description: Invalid request`, so it does not demonstrate a missing description. | Use a `400` or `500` response without `description`; keep the current sample as compliant. |
| `ERROR005` | The sample declares `application/problem+json`, so it satisfies the rule. | Show an error response with another media type or no content, then retain the current sample as compliant. |
| `ERROR006` | The sample includes `WWW-Authenticate`, so it satisfies the rule. | Show a `401` response without that header. |
| `ERROR007` | The sample includes `Allow`, so it satisfies the rule. | Show a `405` response without `Allow`. |
| `ERROR008`–`ERROR009` | The samples show the required `401`/`403` response, so they are compliant. | Show a secured operation missing the required response and retain the current response snippets as compliant. |
| `ERROR010` | The sample has no `WWW-Authenticate` header, so it is compliant with the prohibition. | Show a `403` response that incorrectly includes `WWW-Authenticate`, then retain the current sample as compliant. |
| `FIELD001`, `FILTER001`, `PAGE001`, `PAGE004`, `PAGE005`, `SORT001` | Each sample supplies the capability the rule says is missing or invalid. | Add an operation/parameter/response without the capability as the violation example; label the current fragment compliant. |
| `SEC006` | `customer_id` is declared as `type: string`, so it passes the “must be a string” check. | Use `type: integer` for the violation example. |
| `SEC007` | `customer_id` declares `format: uuid`, so it passes the required-format check. | Omit the format or use a different format for the violation example. |
| `SEC001`–`SEC005`, `SEC008` | These fragments appear to demonstrate the sensitive-name/search/header condition, but are not labelled. | Keep them as violation examples and add explicit compliant examples showing a non-sensitive name or reviewed alternative. |

`FILTER002`, `PAGE002`, `PAGE003`, `PAGE006`, `SORT002`, `SORT003`, and
`SORT004` have no sample at all. Each needs both a wrong representation and a
correct representation. `CONTENT001`–`CONTENT004` likewise need examples for
missing, wildcard, and disallowed media types.

## Specification correctness observations

The samples are mostly intentionally small OpenAPI fragments. That is a good
format for rule documentation, but every document should say where the fragment
belongs when it is not obvious. For example:

- `properties:` belongs under a schema object;
- `parameters:` belongs under an operation or path item;
- `headers:` belongs under a response object;
- `content:` belongs under a request body or response;
- `paths:` belongs at the OpenAPI document root.

The review found no general need to turn every example into a large standalone
specification. A minimal fragment is clearer, provided it is labelled and its
insertion point is clear.

The main correctness risk is semantic rather than YAML syntax: a sample can be
valid OpenAPI and still be the wrong example for the rule. The rules listed in
the sample-correction table should be fixed before the catalogue is treated as
authoritative compliance guidance.

## Remaining improvement order

1. Add executable documentation tests that load each example at its declared
   insertion point and assert that the violation example matches while the
   compliant example does not.
2. Add explicit compliant examples to `CASE008` and `STANDARD024`, and rename
   ambiguous example headings in `DOC007` and `DOC008`.
3. Add the missing intent sections to the B-rated rules where the rule is
   intended for human policy authors rather than only the generated catalogue.

## Review limitation

This was a repository-wide documentation and matcher-semantics review. It did
not change the rule files or claim that every fragment has been executed as a
complete OpenAPI document. The next useful step is an example-test harness that
turns the two examples for each rule into fixtures and evaluates them through
the same Distill runtime used by the policy engine.
