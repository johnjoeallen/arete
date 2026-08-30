# Distill reference

Matchers are written in Distill (`Matcher.dsl`). A Distill script
*distills* the API model down to the occurrences that violate a rule: it is a
single expression that walks `api`, keeps what matches, and returns a list of
`occurrence(...)` values.

It is deliberately small — one expression, no statements, no local variables,
no user-defined functions — and safe by construction: the interpreter exposes
only the immutable `api` and `rule` values, a fixed set of builtins, and
[RE2/J](https://github.com/google/re2j) regular expressions. There is no I/O,
reflection, recursion, or unbounded iteration.

The build also runs a matching `Matcher.groovy` for some matchers as a parity
check; Groovy is not part of the deployed runtime. A deployed Areté always
evaluates matchers with Distill.

## The entry point

```java
distill(api, rule) {
    return <expression>;
}
```

That is the whole grammar at the top level: the keyword `distill`, two parameter
names (`api` and `rule` by convention), then `return` **one** expression
terminated by `;`. The expression must evaluate to a list of occurrences
(often built with `.map { … -> occurrence(...) }`); returning more than 1000
occurrences is a matcher error.

- `api` is the deep-immutable API model. Its shape (`api.paths`,
  `path.operationDetails`, `api.schemas`,
  `schema.properties`, `api.info`, `api.security`, …) is documented under
  [The `api` model](policy-engine.md#the-api-model).
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

- `+` is the **only** arithmetic operator — there is no `-`, `*`, `/` or `%`
  binary operator (`-a` is unary negation only). Matchers compare and count;
  they do not do arithmetic.
- `+` on two numbers is **integer** addition. To build a message string from a
  number write `"" + n`, which takes the string-concatenation branch.
- A string or regex literal is never mistaken for an operator: `"-"`, `"."`
  and `"=="` are ordinary strings.
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
- **string** `[key]` → same as `value.name`, so only `s["length"]` is
  meaningful; strings are **not** indexable by character position (`s[0]` is
  `null`). Use `s.startsWith(...)` / regex instead.

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
| `xs.group { x -> key }` | a map of key → list of items with that key, in first-seen key order (keys compared by string value); iterate it with `.values` |
| `xs.toList()` | shallow copy |

`expand` is the Distill form of a nested loop:

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

Distill supports a slashy regex literal. `/pattern/` is a regex **wherever an
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

## Worked examples

### Expressions

Each row is a complete expression and the value it produces.

| Expression | Result |
|---|---|
| `2 + 3` | `5` |
| `"" + 2 + 3` | `"23"` — left to right: `("" + 2)` is `"2"`, then `+ 3` |
| `["x", "y"] + ["z"]` | `["x", "y", "z"]` |
| `[10, 20, 30][-1]` | `30` — negative index counts from the end |
| `[10, 20, 30][5]` | `null` — out of range |
| `type(3)` &nbsp; `type("s")` &nbsp; `type([1])` | `"int"` &nbsp; `"string"` &nbsp; `"list"` |
| `truthy("")` &nbsp; `truthy([])` &nbsp; `truthy(0)` | `true` &nbsp; `true` &nbsp; `true` |
| `tokenize(",", "a,,b")` | `["a", "", "b"]` — empty token kept |
| `size(tokenize(",", "a,,b"))` | `3` |
| `join("|", tokenize(",", "a,,b"))` | `"a\|\|b"` |
| `distinct(["b", "a", "b", null, "a"])` | `["b", "a"]` — `null` dropped, first-seen order |
| `pathSegments("/v1/orders/{id}/items")` | `["v1", "orders", "items"]` — empty and `{…}` segments dropped |
| `parseInt("  42 ")` &nbsp; `parseInt("x", 0)` | `42` &nbsp; `0` |
| `strip("--hi--", "-")` &nbsp; `strip("  hi  ")` | `"hi"` &nbsp; `"hi"` |
| `last(["a", "b"])` &nbsp; `last([])` | `"b"` &nbsp; `""` |
| `"Order-1" ==~ /[A-Za-z]+-[0-9]+/` | `true` |
| `enumerate(["a", "b"]).map { p -> p[0] + "=" + p[1] }` | `["0=a", "1=b"]` |
| `["ax", "ay", "bz"].group { s -> s.startsWith("a") }.values` | `[["ax", "ay"], ["bz"]]` |

### Matchers

Each example is a full `Matcher.dsl` run against the spec beside it; the
output is the list of occurrences (`pointer` &nbsp;\|&nbsp; `path` &nbsp;\|&nbsp; `message`).

**Operations with no `summary`.**

```java
distill(api, rule) {
    return api.paths.expand { path -> path.operationDetails
        .filter { op -> op.summary == null || op.summary.trim() == "" }
        .map { op -> occurrence(op.pointer, op.method + " " + path.path,
            "Operation has no summary") } };
}
```

```yaml
paths:
  /orders:
    get:  { summary: List orders, responses: { '200': { description: ok } } }
    post: { responses: { '201': { description: created } } }
```

```
/paths/~1orders/post  |  POST /orders  |  Operation has no summary
```

**Property names must be camelCase.**

```java
distill(api, rule) {
    return api.schemas.expand { schema -> schema.properties
        .filter { prop -> !(prop.name ==~ /[a-z][a-zA-Z0-9]*/) }
        .map { prop -> occurrence(prop.pointer, schema.name + "." + prop.name,
            "Property '" + prop.name + "' is not camelCase") } };
}
```

```yaml
components:
  schemas:
    Order:
      type: object
      properties:
        orderId:    { type: string }
        created_at: { type: string }
        Total:      { type: number }
```

```
/components/schemas/Order/properties/created_at  |  Order.created_at  |  Property 'created_at' is not camelCase
/components/schemas/Order/properties/Total       |  Order.Total       |  Property 'Total' is not camelCase
```

**One occurrence for the whole API** — the title must end with a configured
suffix. `rule.parameters` is `{ "suffix": "API" }`.

```java
distill(api, rule) {
    return api.info.title.endsWith(rule.parameters["suffix"])
        ? []
        : [occurrence("/info/title", api.info.title,
            "Title should end with '" + rule.parameters["suffix"] + "'")];
}
```

```yaml
info: { title: Payments, version: 1.0.0 }
```

```
/info/title  |  Payments  |  Title should end with 'API'
```

**Duplicate `operationId`** — group by id, then report every operation after
the first in a group. This is the `operation-metadata` matcher's real
`unique-operation-id` shape (blank-id handling elided). `group` is a list of
`[pointer, location, operationId]` entries; `enumerate` supplies the index so
the first entry can be skipped.

```java
distill(api, rule) {
    return api.paths
        .expand { path -> path.operationDetails.map { op ->
            [op.pointer, op.method + " " + path.path, op.operationId] } }
        .group { entry -> "" + entry[2] }
        .values
        .filter { group -> size(group) > 1 }
        .expand { group -> enumerate(group)
            .filter { indexed -> indexed[0] > 0 }
            .map { indexed -> occurrence(indexed[1][0], indexed[1][1],
                "operationId '" + group[0][2] + "' is also used by " + group[0][1]) } };
}
```

```yaml
paths:
  /orders/{id}:
    get: { operationId: getOrder, responses: { '200': { description: ok } } }
    put: { operationId: getOrder, responses: { '200': { description: ok } } }
```

```
/paths/~1orders~1{id}/put  |  PUT /orders/{id}  |  operationId 'getOrder' is also used by GET /orders/{id}
```

## Idioms

Because Distill has no local variables, a few patterns recur.

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
distill(api, rule) {
    return size(distinct(api.paths.map { p -> pathSegments(p.path)[0] })) > rule.parameters["maximum"]
        ? [occurrence("/paths", "API", "Too many top-level resources")]
        : [];
}
```

**"Seen already?" — group, don't accumulate.** `group` replaces a mutable
`seen` map: group the entries by their key, keep the groups with more than one
member, and report all but the first (see the duplicate-`operationId` example
above). `enumerate` then supplies the positional "all but the first" filter,
`indexed[0] > 0`. This is O(n) and needs no repetition.

**List building.** Concatenate independent checks with `+`:

```java
info.extensionKeys.filter { k -> ... }.map { k -> occurrence(...) }
  + api.paths.expand { path -> ... }
```

## Coverage

All 45 bundled matchers ship a `Matcher.dsl`, and all 139 bundled rules are
built on those matchers. The `operation-metadata` matcher handles duplicate
`operationId` values and the `response-example` matcher handles duplicate error
payloads; both use `group` as shown above. `path-count` uses `pathSegments(...)`
to stay within the grammar.

This demonstrates the flexibility of the multi-level matcher/rule model: a
single matcher can support several named rules, while policies decide which
rules are active and can override their parameters for a particular policy.
