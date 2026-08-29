# Sift reference

Sift is the default language for policy-bundle detectors (`Detector.sift`). A
Sift script *sifts* the API model down to the occurrences that violate a rule:
it is a single expression that walks `api`, keeps what matches, and returns a
list of `occurrence(...)` values.

It is deliberately small — one expression, no statements, no local variables,
no user-defined functions — and safe by construction: the interpreter exposes
only the immutable `api` and `rule` values, a fixed set of builtins, and
[RE2/J](https://github.com/google/re2j) regular expressions. There is no I/O,
reflection, recursion, or unbounded iteration.

Every bundled detector also ships `Detector.star` and `Detector.groovy`.
`SiftParityTest` and `GroovyStarlarkParityTest` assert all three produce
identical occurrences, so choosing a runtime never changes a policy's findings.

## The entry point

```java
sift(api, rule) {
    return <expression>;
}
```

That is the whole grammar at the top level: the keyword `sift`, two parameter
names (`api` and `rule` by convention), then `return` **one** expression
terminated by `;`. The expression must evaluate to a list of occurrences
(often built with `.map { … -> occurrence(...) }`); returning more than 1000
occurrences is a detector error.

- `api` is the deep-immutable API model — the same value the Starlark runtime
  receives. Its shape (`api.paths`, `path.operationDetails`, `api.schemas`,
  `schema.properties`, `api.info`, `api.security`, …) is documented under
  [Writing a detector](policy-engine.md#writing-a-detector).
- `rule` is `{ id, scope, parameters }`. Rule configuration is
  `rule.parameters` — e.g. `rule.parameters["max-items"]` or, for a key that
  is a plain identifier, `rule.parameters.suffix`.

There are no comments.

## Values and literals

| Literal | Example | Notes |
|---|---|---|
| String | `"query"` | `\"`, `\\`, `\n` … escapes; double-quoted only |
| Integer | `429` | whole numbers only; no decimal literal |
| Boolean | `true` `false` | |
| Regex | `/[A-Z][a-z]+/` | slashy literal — see [Regular expressions](#regular-expressions) |
| List | `["form", "spaceDelimited"]` | elements are any expression |

There is no map literal. `null` is not a keyword — it arrives from the model
(a missing field) and is produced by builtins such as `find`.

### Truthiness

`false` and `null` are falsy. **Everything else is truthy**, including `""`,
`0`, and `[]`. Use explicit checks (`text != ""`, `size(list) > 0`) rather than
relying on emptiness, and use `truthy(x)` when you need the boolean itself.

## Operators

Highest precedence first:

| Operator | Meaning |
|---|---|
| `a.b`  `a.b(...)`  `a.b { x -> ... }`  `a[k]` | member / method / trailing-closure / index |
| `!a`  `-a` | logical not, numeric negation |
| `a + b` | numeric **integer** add if both are numbers; list concat if both are lists; otherwise string concatenation (`null` renders as `"null"`) |
| `a < b` `a <= b` `a > b` `a >= b` | numeric by value; otherwise lexicographic on strings |
| `a == b` `a != b` | value equality — numbers compare by numeric value, so `8 == 8` across int/long/double |
| `a ==~ r` `a =~ r` | regex full-match / regex search (right side is a regex literal or a pattern string) |
| `a && b` &nbsp;&nbsp; `a` `\|\|` `b` | short-circuit boolean |
| `c ? t : f` | conditional |

Notes:

- `+` on two numbers is **integer** addition. To build a message string from a
  number write `"" + n`, which takes the string-concatenation branch.
- `==` / `!=` are value equality. `parseInt(resp.status, -1) == 404` works even
  though the parsed value is a `long` and the literal an `int`.
- `&&` and `||` short-circuit, so `x != null && x.lower() == "y"` is safe.

## Member access and indexing

`value.name`

- on a **map**: the entry `name`. If there is no such entry, `.keys` and
  `.values` yield the map's keys / values as lists; any other missing name
  yields `null`.
- on a **string**: `.length` (an integer). String *operations* are method
  calls — see below.
- otherwise: `null`.

`value[key]`

- **map** `[string]` → that entry (`m["max-items"]` for keys that are not
  identifiers).
- **list** `[int]` → that element; negative indexes count from the end;
  out-of-range yields `null`.
- **string** `[name]` → same as `value.name`.

## Methods

### String methods

| Call | Result |
|---|---|
| `s.lower()` | lower-cased copy |
| `s.trim()` | whitespace-trimmed copy |
| `s.contains(t)` | boolean |
| `s.startsWith(t)` | boolean |
| `s.endsWith(t)` | boolean |
| `s.length` | length (a member, not a call) |

### Sequence methods

Each takes a **trailing closure** `{ item -> expression }` (except `toList`).
Closures have exactly one parameter and one expression body, and may only
appear in trailing position.

| Call | Result |
|---|---|
| `xs.map { x -> ... }` | list of the closure results |
| `xs.filter { x -> ... }` | items for which the closure is truthy (`match` is an alias) |
| `xs.expand { x -> ... }` | closure returns a list per item; results are flattened one level |
| `xs.any { x -> ... }` | `true` if the closure is truthy for some item |
| `xs.all { x -> ... }` | `true` if the closure is truthy for every item |
| `xs.find { x -> ... }` | first matching item, or `null` |
| `xs.count { x -> ... }` | number of matching items |
| `xs.toList()` | shallow copy |

`expand` is the Sift form of a nested loop:

```java
api.paths.expand { path -> path.operationDetails.map { operation ->
    occurrence(operation.pointer, operation.method + " " + path.path, "…") } }
```

## Builtin functions

| Function | Result |
|---|---|
| `occurrence(pointer, path, message)` | an occurrence; `pointer` and `path` may be `null`, `message` must be non-blank. Non-string arguments are stringified. |
| `regexFullMatch(pattern, text)` | whole-string match; `pattern` is a regex literal or a string |
| `regexSearch(pattern, text)` | match anywhere in `text` |
| `tokenize(delim, text)` | split `text` on the **literal string** `delim`; empty tokens are kept (pair with `.filter { t -> t != "" }`) |
| `size(list)` | element count (an integer) |
| `distinct(list)` | de-duplicated list — drops `null`, keeps first-seen order, compares by string value |
| `parseInt(text)` / `parseInt(text, fallback)` | base-10 integer after trimming; `fallback` (default `-1`) on failure |
| `join(sep, list)` | elements joined with `sep` |
| `urlHost(url)` | host component of `url`, or `null` |
| `strip(text)` / `strip(text, chars)` | one arg trims whitespace; two args strip any character in `chars` from both ends |
| `last(list)` | last element, or `""` when empty |
| `type(value)` | `"string"`, `"int"`, `"float"`, `"bool"`, `"dict"`, `"list"`, `"NoneType"`, or `"object"` |
| `truthy(value)` | the boolean truth value (`false`/`null` → `false`, everything else → `true`) |
| `enumerate(list)` | list of `[index, value]` pairs; `index` is an integer |
| `pathSegments(path)` | the `/`-separated segments of `path`, dropping empty segments and `{templated}` ones |

Anything outside this list is a deliberate, reviewed addition to the runtime —
not something a script can reach around.

## Regular expressions

Sift borrows Groovy's slashy literal. `/pattern/` is a regex **wherever an
operand is expected** — after `==~`, `=~`, `(`, `,`, `&&`, `||`, `return`, and
so on. The explicit `~/pattern/` form is a regex anywhere. Inside the literal,
backslashes are taken literally — write `\b`, `\d`, `\.` — and only `\/` is an
escaped slash.

```java
name ==~ /[a-z][a-zA-Z0-9]*/            // whole-string match
path.path =~ /(?i)\/v[0-9]+\//          // search
regexFullMatch("(?i).*\\berror\\b.*", text)   // string pattern: normal Java escaping
```

`a ==~ r` is a whole-string match, `a =~ r` is a search. The engine is RE2/J:
linear-time, no backreferences, no catastrophic backtracking.

## Idioms

Because Sift has no local variables, a few patterns recur.

**Repeat, don't bind.** A value used several times is written out each time.
Keep sub-expressions small and let `filter` / `map` carry the structure.

**Optional single occurrence.** Use a list literal for the "emit one" and "emit
nothing" branches, then let the surrounding `expand` flatten it:

```java
api.paths.expand { path ->
    size(pathSegments(path.path)) > rule.parameters["maximum-depth"]
        ? [occurrence(path.pointer, path.path, "Path nests too deeply")]
        : [] }
```

**Whole-API single occurrence.** Same idea at the top level:

```java
sift(api, rule) {
    return size(distinct(api.paths.map { p -> pathSegments(p.path)[0] })) > rule.parameters["maximum"]
        ? [occurrence("/paths", "API", "Too many top-level resources")]
        : [];
}
```

**"Seen already?" without an accumulator.** Scan `enumerate(...)` for an
earlier entry that matches — this replaces a mutable `seen` set:

```java
enumerate(entries).expand { pair ->
    enumerate(entries).find { earlier -> earlier[0] < pair[0] && earlier[1].key == pair[1].key } == null
        ? []
        : [occurrence(pair[1].pointer, pair[1].loc, "Duplicate of an earlier entry")] }
```

**List building.** Concatenate independent checks with `+`:

```java
info.extensionKeys.filter { k -> ... }.map { k -> occurrence(...) }
  + api.paths.expand { path -> ... }
```

## Coverage

All 45 bundled detectors ship a `Detector.sift`. The two that need a running
accumulator — `operation-metadata` (duplicate `operationId`) and
`response-example` (duplicate error payloads) — use the `enumerate(...)` scan
shown above. `path-count` uses `pathSegments(...)` to stay within the grammar.
