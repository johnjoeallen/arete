# Detector languages

Speculate currently demonstrates the operation-semantics detector in three
styles: Groovy, Starlark, and the experimental Sift (`.sift`) syntax.
All three examples express the same core pipeline: visit paths, flatten their
operations, retain matching operations, and create occurrences.

## The same detector in three languages

### Groovy

```groovy
{ Map api, Map rule ->
    def parameters = rule.parameters ?: [:]
    def mutation = /(?i)\b(create|update|delete|remove|activate|deactivate|cancel|change|set)\b/
    def replacement = /(?i)\b(replace|replacement)\b/
    def partial = /(?i)\b(partial|patch|update)\b/

    def matches = { path, operation ->
        if (parameters.method && operation.method != parameters.method) return false
        def text = ((path.path ?: '') + ' ' + (operation.summary ?: '')).trim()
        def getMutation = operation.method == 'GET' && text ==~ /.*${mutation}.*/
        def postReplacement = operation.method == 'POST' && path.path ==~ /.+\/\{[^}]+\}.*/ && text ==~ /.*${replacement}.*/
        def putPartial = operation.method == 'PUT' && text ==~ /.*${partial}.*/

        if (parameters.expected == 'safe') return getMutation
        switch (parameters.match) {
            case 'full-resource-replacement': return postReplacement
            case 'partial-update': return putPartial
            case 'inconsistent-method-resource-semantics': return getMutation || postReplacement
            default: return false
        }
    }

    api.paths.collectMany { path ->
        path.operationDetails.findAll { operation -> matches(path, operation) }.collect { operation ->
            [pointer: operation.pointer, path: operation.method + ' ' + path.path, message: 'Operation semantics need review']
        }
    }
}
```

Groovy is expressive and close to the original detector implementations, but
it runs with the authority of the JVM and is therefore an opt-in fallback.

### Starlark

```python
_MUTATION = r"(?i).*\b(create|update|delete|remove|activate|deactivate|cancel|change|set)\b.*"
_REPLACEMENT = r"(?i).*\b(replace|replacement)\b.*"
_PARTIAL = r"(?i).*\b(partial|patch|update)\b.*"
_IDENTIFIED_RESOURCE = r".+/\{[^}]+\}.*"

def matches(path, operation, parameters):
    method = parameters.get("method")
    if method and operation["method"] != method:
        return False

    text = ((path["path"] or "") + " " + (operation["summary"] or "")).strip()
    get_mutation = operation["method"] == "GET" and re_fullmatch(_MUTATION, text)
    post_replacement = (operation["method"] == "POST"
                        and re_fullmatch(_IDENTIFIED_RESOURCE, path["path"])
                        and re_fullmatch(_REPLACEMENT, text))
    put_partial = operation["method"] == "PUT" and re_fullmatch(_PARTIAL, text)

    if parameters.get("expected") == "safe":
        return get_mutation
    match = parameters.get("match")
    if match == "full-resource-replacement":
        return post_replacement
    if match == "partial-update":
        return put_partial
    if match == "inconsistent-method-resource-semantics":
        return get_mutation or post_replacement
    return False

def detect(api, rule):
    parameters = rule["parameters"]
    out = []
    for path in api["paths"]:
        for operation in path["operationDetails"]:
            if matches(path, operation, parameters):
                out.append({
                    "pointer": operation["pointer"],
                    "path": operation["method"] + " " + path["path"],
                    "message": "Operation semantics need review",
                })
    return out
```

Starlark makes the data model and control flow explicit. It is the default
runtime and is sandboxed by construction.

### Sift

```java
sift(api, rule) {
    return api.paths
        .expand { path -> path.operationDetails
            .filter { operation ->
                (rule.parameters.method == null || operation.method == rule.parameters.method)
                && (
                    (rule.parameters.expected == "safe"
                        && operation.method == "GET"
                        && path.path + " " + operation.summary ==~ /(?i).*\b(create|update|delete|remove|activate|deactivate|cancel|change|set)\b.*/)
                    || (rule.parameters.match == "full-resource-replacement"
                        && operation.method == "POST"
                        && path.path ==~ /.+\/\{[^}]+\}.*/
                        && path.path + " " + operation.summary ==~ /(?i).*\b(replace|replacement)\b.*/)
                    || (rule.parameters.match == "partial-update"
                        && operation.method == "PUT"
                        && path.path + " " + operation.summary ==~ /(?i).*\b(partial|patch|update)\b.*/)
                    || (rule.parameters.match == "inconsistent-method-resource-semantics"
                        && (
                            (operation.method == "GET"
                                && path.path + " " + operation.summary ==~ /(?i).*\b(create|update|delete|remove|activate|deactivate|cancel|change|set)\b.*/)
                            || (operation.method == "POST"
                                && path.path ==~ /.+\/\{[^}]+\}.*/
                                && path.path + " " + operation.summary ==~ /(?i).*\b(replace|replacement)\b.*/)))
                )
            }
            .map { operation -> occurrence(
                operation.pointer,
                operation.method + " " + path.path,
                operationMessage(rule.parameters)) } };
}
```

Sift keeps Java-shaped operators and declarations while using
Groovy-like trailing closures for collection pipelines. `expand` names the
operation that turns each path into its operation collection; it is the
Sift equivalent of Groovy `collectMany` and Starlark’s nested loop.

For regular expressions Sift borrows Groovy's slashy literal and match
operators. `/pattern/` is a pattern wherever an operand is expected — after
`==~`, `=~`, `(`, `,`, `&&`, `||`, `return`, and so on — with backslashes
taken literally (write `\b`, not `\\b`); only `\/` is an escaped slash. The
explicit `~/pattern/` form is accepted anywhere. `text ==~ /…/` is a
whole-string match, `text =~ /…/` is a search, and the
`regexFullMatch(pattern, text)` / `regexSearch(pattern, text)` functions
remain available and accept either a literal or a string.

## Syntax comparison

| Operation | Groovy | Starlark | Sift |
|---|---|---|---|
| Transform values | `collect { value -> ... }` | list comprehension or loop | `.map { value -> ... }` |
| Keep matching values | `findAll { value -> ... }` | `if` in a comprehension or loop | `.filter { value -> ... }` |
| Flatten nested values | `collectMany { value -> ... }` | nested `for` loop | `.expand { value -> ... }` |
| Read a field | `value.field` | `value["field"]` | `value.field` |
| Create a finding | `[pointer: ..., path: ..., message: ...]` | `{"pointer": ..., "path": ..., "message": ...}` | `occurrence(pointer, path, message)` |
| Regular expressions | `/…/` with `==~` / `=~` | `re_fullmatch(...)` | `/…/` with `==~` / `=~` (or `regexFullMatch(...)`) |
| Entry point | closure `{ Map api, Map rule -> ... }` | `detect(api, rule)` | `sift(api, rule) { ... }` |

## Choosing a language

Starlark is the default for bundled detectors because it provides a restricted,
deterministic execution environment. Groovy remains available as an explicit,
unsandboxed fallback for compatibility. Sift is a prototype focused
on making detector pipelines feel natural to Java developers while retaining a
small, controlled operation set.

The language precedence and Groovy opt-in switches are documented in the
[policy engine guide](policy-engine.md#detector-languages).

## Sift coverage

Sift is expression-only: a single `return`, closures with one parameter and a
one-expression body, no local variables or helper functions. Beyond the core
operators it has `< <= > >=`, unary `-`, `true` / `false`, `/regex/` literals
with `==~` / `=~`, list concatenation with `+`, `value[key]` indexing, string
methods (`lower` `trim` `contains` `startsWith` `endsWith` `length`), sequence
methods (`map` `filter` `expand` `any` `all` `find` `count` `toList`) and the
functions `occurrence` `regexFullMatch` `regexSearch` `tokenize` `size`
`distinct` `parseInt` `join` `urlHost` `last`.

It still has **no** `type(x)` introspection, no map-key iteration, no list or
map literals, and no cross-iteration accumulators.

**29 of the 45 bundled detectors ship a `Detector.sift`**, each parity-tested
against its `Detector.star` (see `SiftParityTest`): `authentication-error`,
`bulk-operation`, `collection-capability`, `common-field`, `compatibility`,
`date-time-name`, `document-lint`, `documentation-completeness`, `enum-values`,
`error-response`, `header-schema`, `hostname`, `identifier`, `manual`,
`media-type`, `openapi-version`, `operation`, `operation-semantics`,
`pagination`, `parameter`, `path-set`, `request-body`, `resource-path`,
`schema-composition`, `schema-name`, `sensitive-data`, `sensitive-search`,
`server-url`, `status-class`.
