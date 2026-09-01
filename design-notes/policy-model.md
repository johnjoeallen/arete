# The policy model

> **Implemented.** Terminology settled in v0.99.105 (RuleSet folded into
> Policy) and v0.99.104 (Validation → Scoring). See
> [`docs/scoring/policy-engine.md`](../docs/scoring/policy-engine.md) and
> [`docs/scoring/policies.md`](../docs/scoring/policies.md) for the shipped
> reference.

## The problem

Two names had grown for the same thing. The scoring plugin SPI spoke of a
**rule set** (`getRuleSets()`, `DEFAULT_RULE_SET`, `SpecInput.ruleSet`), while
the bundle and docs spoke of a **policy**. They were always the same object —
one deployable configuration a user picks before clicking **Score** — but the
split wording made the docs and the UI read as if there were two concepts.
Separately, the user-facing feature was called "Validation" in some places and
"Score/Scoring" in others.

## The model

**Scoring** is the feature: on demand, run a spec against a chosen
configuration and get findings plus a numeric score, a letter grade, and a
pass/fail verdict.

A **policy** is that configuration — **a set of rules**. Concretely, a policy:

1. **names the active rules** and, for each, its **score deduction** (a number
   `0`–`100`) or **`PROHIBITED`** (any match forces the overall score to 0);
2. **may override a rule's default parameters** — a rule ships sensible
   defaults in its descriptor; a policy can supply its own values for that
   policy's runs;
3. **may set a `passingScore`** — the bar the overall score must clear for the
   run to pass;
4. **may set an explicit grade set** (`grades:` — label → minimum score,
   highest first). If omitted, bands are **derived from `passingScore`**
   (`C` = the pass mark, `A`/`B` above, `D` below); if there is no
   `passingScore` either, the **default `A ≥ 90, B ≥ 80, C ≥ 70, D ≥ 60`**
   applies. A `+`/`-` is appended for a score in the top or bottom third of a
   band. So every policy always yields a grade.

A rule on its own does nothing — it is only evaluated when a policy references
it. Matchers (Distill programs) are the shared machinery a rule points at; the
rule binds a matcher to a scope and parameters; the policy decides which rules
run and what each match costs.

```
qualityScore   = max(0, 100 − Σ deductions for matched rules)   # once per rule
overallScore    = 0 if any PROHIBITED rule matched, else qualityScore
grade           = gradeFor(overallScore)   # see above
passes          = passingScore is unset, or overallScore ≥ passingScore
```

## Naming decisions

| Old | New | Rationale |
|---|---|---|
| Validation (the feature) | **Scoring** | the user-facing verb is "Score"; the output is a score/grade/verdict |
| `getRuleSets()` / rule set | **`getPolicies()` / policy** | one word for the one concept a user selects |
| `DEFAULT_RULE_SET` | `DEFAULT_POLICY` | |
| `SpecInput.ruleSet` | `SpecInput.policy` | |
| `SpecScoringPlugin.validate(SpecInput)` | `.score(SpecInput)` | |
| UI column "Rule set" | "Policy" | |

**Kept deliberately:**

- `BundleValidationException` and the `validate(...)` helpers for slugs, URLs,
  and matcher compilation — these check *well-formedness*, which is genuinely
  validation, not scoring.
- The automation API's `run=<validator>/<policy>` request field keeps
  **`validator`** for the plugin — a stable request-contract name, and the
  plugin genuinely is a validator of the spec even though its output is a
  score.
- The physical DB tables `spec_validation_results` and column `rule_set_index`
  are unchanged, so a user's existing local scores survive the rename with no
  migration.

## SPI impact

`arete-scoring-spi` (renamed from `arete-validation-spi`) is published to Maven
Central, so this is a **breaking change** for any external plugin: new
coordinates (`net.dublinux.arete:arete-scoring-spi`), new package
(`net.dublinux.arete.scoring.spi`), `SpecScoringPlugin` with `score(...)` and
`getPolicies()`. The bundled `arete-policy-plugin` is the only known consumer.
