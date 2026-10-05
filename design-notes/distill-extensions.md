# Distill beyond simple matching

> **Status: values done; the rest undecided.** The policy side is done (see "How a rule is charged" in the policy-engine docs):
> `expect: match`, `per-match`, `max` and `tiers` work on the matches a matcher returns. What is left is what a matcher can
> *say*. This note is for deciding that before any grammar changes.

## What the policy side cannot do yet

A matcher returns a list of occurrences, each a violation: a pointer, a subject label and a message. So:

- `expect: match` can only mean "at least one match anywhere in the spec". "Every operation has a security requirement"
  needs the matcher to report the operations that were checked, and which passed.
- A limit that is not a count of occurrences ("an operation has at most 8 parameters", "nesting no deeper than 4") is
  written today as a filter that emits one occurrence per offender. That works, but the policy cannot see the *value*
  (9 parameters) to tier on it, and the message has to repeat the number.
- A rule that is true of the whole spec (at least one security scheme is defined; all list operations paginate the same way)
  has no subject to point at, so it reports at `/` or `/paths`.

## Candidates

1. **Counts and values. Done.** An occurrence may carry a number (`occurrence(pointer, subject, message, value: 9)`), which
   a policy can tier on (`tiers: {9: 1, 13: 3}` over the value instead of the count of occurrences). Decided: `occurrence(..., value)`, and
   a policy says `measure: value` with `tiers`; the worst value is charged and the gate compares it. JSON025 (nesting depth)
   is the real rule that drove it, and STANDARD011 reports its parameter count.
2. **Coverage. Dropped.** A matcher may report the subjects it examined, so `expect: match` can mean "every subject matches" and name
   the ones that do not, at their own pointers. Dropped as redundant: a rule that wants every
   subject to match is written as a matcher that reports the ones that do not (the existing model), so there is nothing
   a coverage list adds. Revisit only if a real rule cannot be written that way.
3. **Whole-spec scope. Deferred** until a real rule needs it. A scope of `api` that is a first-class subject with its own pointer (`/`), instead of borrowing
   `/paths` or `/info`.
4. **Combinators. Deferred** until a real rule needs it. `all`, `any` and `not` over matchers, so a rule is built from smaller ones ("a POST that is not a
   search and has no 201") without a new matcher program each time. Distill is closed on purpose (no I/O, no recursion, a
   fixed builtin set): a combinator must keep it that way.
5. **The resolved schema graph.** `nestingDepth` is now exposed to matchers (JSON025). `schema.properties` already lists nested properties; a matcher could also walk references,
   with `nestingDepth` and `refProblems` already in the model.

   **Threshold defaults (done).** A matcher that reports only past a threshold of its own (`maximum`, `minimum`) would hide a
   tier below the rule's default. When a policy charges by value and omits that parameter, the engine derives it from the
   lowest tier: `maximum` = tier − 1, `minimum` = tier. An explicit value wins; `maximum: 0` is the "list everything" setting.

## Constraints

- Distill stays a safe expression language: no loops, no I/O, no reflection. Anything added is a builtin or a model field.
- Existing matchers and policies keep their meaning. New behaviour is opt-in (a policy `format`, a new matcher parameter, a
  new model field), and the rule corpus pins every bundled rule before and after.
- Occurrence identity (rule, pointer, subject) must stay stable, because the merge-gate matches findings across two runs
  by it.

## Next

Pick one real rule that needs each of 1 and 2, write it by hand as the policy author would want to, and let that decide the
syntax. Do not design the grammar from the list.
