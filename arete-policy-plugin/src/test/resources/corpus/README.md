# Rule corpus

One directory per bundled rule under `violations/<RULE>/`:

| File | Meaning |
| --- | --- |
| `bad.yaml` | A small, complete OpenAPI document that breaks the rule. |
| `good.yaml` | The same API with only that problem fixed. |
| `bad.findings` | The findings the rule reports on `bad.yaml`: pointer and message, sorted. |
| `parameters.properties` | Optional. Rule parameters to apply over the rule's own, for a rule whose default no parsable document can violate (e.g. STANDARD010). |
| `pending.txt` | Optional. The rule cannot yet report its documented violation; the test is skipped and the file says why. |

`RuleCorpusTest` runs each rule on its own, whatever policy would include it: it must
report on `bad.yaml` at the recorded pointers, and stay silent on `good.yaml`.

After an intended change to a rule, a matcher or the model, review the diff and regenerate:

```
mvn -pl arete-policy-plugin test -Dtest=RuleCorpusTest -Dcorpus.update=true
```

A rule with a `pending.txt` is a known gap, not a passing test. Today:

- `COMPAT001`–`COMPAT006` compare against a baseline spec, which the engine does not take yet.
- `HTTP008` and `UPDATE003` are deliberately inert (a no-op vocabulary, and a manual-review rule).
- `JSON013` cannot fire because the parser coerces enum values to the property's type.
