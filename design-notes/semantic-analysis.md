# Semantic analysis: whole-spec consistency

> **Proposed.** Nothing here is implemented. This note records the decisions
> taken so far and the open questions.

## The problem

Distill rules judge one operation, schema or path set at a time. That catches
"does this endpoint follow the rules", but not whether the API as a whole is
coherent: the same concept under three names, collections that paginate three
different ways, a child resource with no coherent parent. Those problems only
show when the whole spec is compared against itself.

Put differently: Distill rules validate against a rigid rule set. An
occurrence either matches or it does not, though a rule can be charged in
grades (`points`, `per-match`, or `tiers` on a count or a measured value).
For example, a missing 201 costs 0.5 in one operation, 1.0 in ten, and 5 when
it is missing everywhere. Semantic analysis is more nuanced: whether two names
denote one concept is a judgement, not an exact match, and the unit counted is
not a single stanza match but a *conflict group* found across the whole spec.
It is scored the same way, though: by how many are found.

OpenAPI linting checks how an API is *described*. Semantic analysis checks what
it *means* and how usable it is, without any AI: static analysis over a model
of resources, operations and schemas, plus a domain vocabulary.

## Scope (decided)

- **Whole-spec, one spec at a time.** The analysis sees an entire OpenAPI
  document at once. Comparing *across* specs (org-wide duplication) is out of
  scope; it needs a store of models from many specs and is a later step. The
  app's `SpecEntity` and namespaces are the nearest existing seam.
- **First target: cross-endpoint consistency.** Consumer-journey analysis comes
  later.
- **Vocabulary lives in the policy bundle**, not the engine.
- **Usability findings affect the score** (see Scoring).

## The semantic model

A `SemanticModel` built from the parsed spec, as a new module in
`arete-engine` beside `OpenApiMapAdapter` (which already produces the map/`Refs`
view the Distill evaluator reads):

- **Resources** — derived from path structure, normalised and stemmed with the
  existing separator-aware `words()`/`normalise()` (UK and US spellings).
- **Relationships** — parent/child from nested paths and identifier parameters.
- **Operations** — method, resource, kind (CRUD or action), request/response
  schema references.
- **Schema fingerprints** — property names and types, for equivalence checks.

Plain Java records; no graph library to start with. The model is built so a
second input format could feed it, but it is not abstracted over OpenAPI yet.

## Vocabulary

A `vocabulary.yaml` in the bundle, loaded by `PolicyBundleLoader` alongside
rules and tiers:

```yaml
concepts:
  customer:
    synonyms: [client, banking-customer]
  account:
    synonyms: [bank-account]
```

Equivalence is defined only by the domain, and the default is closed: `user` is
**not** assumed to equal `customer` because the vocabulary has no mapping
between them.

Absence is not enough in two cases: removing a mapping a parent policy
declares, and stopping a heuristic (structural schema similarity) from
equating two things the vocabulary never linked. So the merge is explicit:

```yaml
concepts:
  customer:
    synonyms: [banking-customer]   # adds to the parent's list
    remove-synonyms: [client]      # drops an inherited synonym
distinct:
  - [user, customer]               # never equivalent, from any source
```

- Inheritance is **additive** by default: a child's `synonyms` extend the
  parent's.
- `remove-synonyms` is the only way to drop an inherited synonym, and names
  exactly what it removes.
- `distinct` blocks equivalence from every source: an inherited mapping and a
  heuristic match alike.

A child overriding its parent is intentional, not an error. Per the existing
convention only a conflict *inside one* policy is an error, for example
declaring two names synonyms and distinct in the same policy.

## Finding classes

| Class | Examples | Behaviour |
| --- | --- | --- |
| Deterministic | contradictory parameter types, broken parent/child references, inconsistent schema refs | normal deduction |
| Suspicion | possible duplicate concept, inconsistent terminology, questionable ownership | lower tier, small deduction, flagged for review |
| Usability | excess requests, fan-out, missing collection operation (needs journeys) | deduction per violated constraint |

Because the verdicts are judgements, every semantic finding carries the
**evidence** behind it: the matched tokens, synonyms and schema properties. The
evidence goes in the `Diagnostic` so a reviewer can accept or dismiss it.
Confidence is a **gate**, not a scoring input: a similarity threshold (a rule
parameter a policy can override, like `maximum`/`minimum` today) decides whether
a candidate counts as a conflict. Once it does it is one occurrence, and the
deduction comes from `tiers` on the number of conflicts found, exactly as for
Distill rules. The score never depends on a fine-grained confidence value.

Static analysis cannot prove runtime behaviour (that `GET` has no side effects).
It reports design contradictions and suspicious patterns only.

## Exposure

A new matcher kind with its own `Matcher.md`, not new Distill built-ins. The
logic is graph-shaped and spans the document, which does not fit a per-stanza
filter/map language. Findings use the existing `Diagnostic` and `ScoreReport`.

## Scoring

Deterministic and suspicion findings use the existing deduction system
(`points`, `per-match`, `tiers`). Conflicting concepts, for instance, can be
charged by `tiers` on how many conflict groups the spec contains.

Usability findings also deduct, but from **explicit journey constraints**
(`maxRequests`, `maxDependencyDepth`, `clientAggregation`), not from a weighted
cost. The cost formula `C = αR + βD + γF + δS` is a report metric only: a score
driven by tuned weights would be hard to explain and to reproduce. Thresholds
are explicit and auditable.

## Accepting a finding

The analysis is repeatable (same spec, vocabulary and thresholds give the same
findings) but fuzzy, so it will produce false positives that a human dismisses,
and the dismissal must survive re-runs. Three mechanisms, in order of
preference:

1. **Vocabulary.** Many false positives are missing domain knowledge. The
   vocabulary can declare two concepts *distinct* (`user` is not `customer`),
   which removes the finding for every spec with no per-finding waiver.
2. **`.arete.yaml` `accept:` entries.** The primary per-project route. Each
   entry names the rule, a stable finding key and a required `reason`, in the
   existing team-overrides file, reviewed in the project's repo and separate
   from the spec being judged. Today's overrides work per rule (disable,
   points, parameters); this adds per-finding acceptance.
3. **In-spec hint.** An `x-arete-accept` extension on the path, operation or
   schema, with a rule and a `reason`. It travels with the spec and sits beside
   what it excuses, but the author is waiving their own finding, so the policy
   governs it: a `locked` rule cannot be accepted, and a policy can ignore spec
   hints altogether.

Rules common to all three:

- **Stable finding key.** A fingerprint of the conflict group: rule id plus the
  sorted, normalised names or paths involved. No line numbers, so an unrelated
  edit does not invalidate an acceptance.
- **Stale acceptances fail loudly.** An acceptance matching no finding is
  reported, as an override of an unknown rule is an error today, so dead waivers
  do not accumulate.
- **Accepted findings stay visible.** They are removed from the `tiers` count,
  so the score does not charge for them, but the report lists them as accepted
  with their reason, so waivers are auditable.

## Phases

1. **Vocabulary, naming consistency, duplicate-concept detection.**
2. **Schema equivalence, pagination and error-contract consistency, lifecycle
   completeness, relationship integrity.**
3. **Declarative `journeys` in the bundle**, graph search from inputs to
   outputs, constraint violations as scored findings. Usability only scores
   from this phase.

## Open questions

- Shape of the `accept:` entry and of the finding key for each rule kind, and
  whether `x-arete-accept` is honoured by default or only when a policy opts in.

- Matcher kind name and descriptor shape for the whole-spec matcher.
- Schema-similarity threshold: fixed, or derived from the lowest tier as for
  value-measured rules.
- Journey input/output typing: by schema name, by identifier, or by vocabulary
  concept.
- Deferred from the Distill tokenisation work: resource-path segment
  sub-delimiters (`:` and `.`) matter here, since resource extraction depends on
  them.
