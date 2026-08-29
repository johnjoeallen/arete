# Detector languages

Speculate currently demonstrates the operation-semantics detector in three
styles: Groovy, Starlark, and the experimental DetectorScript (`.ds`) syntax.
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

### DetectorScript

```java
detector(api, rule) {
    return api.paths
        .expand { path -> path.operationDetails
            .filter { operation -> operation.method == "GET"
                && regexFullMatch("(?i).*\\b(create|update|delete|remove|activate|deactivate|cancel|change|set)\\b.*",
                    path.path + " " + operation.summary) }
            .map { operation -> occurrence(
                operation.pointer,
                operation.method + " " + path.path,
                "GET operation appears to mutate state") }
            }
            ;
}
```

DetectorScript keeps Java-shaped operators and declarations while using
Groovy-like trailing closures for collection pipelines. `expand` names the
operation that turns each path into its operation collection; it is the
DetectorScript equivalent of Groovy `collectMany` and Starlark’s nested loop.

## Syntax comparison

| Operation | Groovy | Starlark | DetectorScript |
|---|---|---|---|
| Transform values | `collect { value -> ... }` | list comprehension or loop | `.map { value -> ... }` |
| Keep matching values | `findAll { value -> ... }` | `if` in a comprehension or loop | `.filter { value -> ... }` |
| Flatten nested values | `collectMany { value -> ... }` | nested `for` loop | `.expand { value -> ... }` |
| Read a field | `value.field` | `value["field"]` | `value.field` |
| Create a finding | `[pointer: ..., path: ..., message: ...]` | `{"pointer": ..., "path": ..., "message": ...}` | `occurrence(pointer, path, message)` |
| Regular expressions | Groovy regex literals and `==~` | `re_fullmatch(...)` | `regexFullMatch(...)` |
| Entry point | closure `{ Map api, Map rule -> ... }` | `detect(api, rule)` | `detector(api, rule) { ... }` |

## Choosing a language

Starlark is the default for bundled detectors because it provides a restricted,
deterministic execution environment. Groovy remains available as an explicit,
unsandboxed fallback for compatibility. DetectorScript is a prototype focused
on making detector pipelines feel natural to Java developers while retaining a
small, controlled operation set.

The language precedence and Groovy opt-in switches are documented in the
[policy engine guide](policy-engine.md#detector-languages).
