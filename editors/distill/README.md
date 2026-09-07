# Distill editor support

Syntax highlighting for [Distill](../../docs/scoring/distill.md) — the
Java-shaped fluent rule language in `api-policy/matchers/*/Matcher.dsl`.

This directory is a VS Code extension **and** a TextMate bundle. Both editors
use the same `distill.tmLanguage.json` grammar.

## What it highlights

- keywords (`distill`, `return`, `true`, `false`) and the `is blank` operator
- the Distill-specific operators `?:`, `?.`, `==~`, `=~`, `->`
- string literals with `{{ … }}` interpolation holes (the hole re-enters the grammar)
- regex literals — `~/…/` and bare `/…/`
- bundled functions (`count(`, `words(`, …) vs. bundled members (`.summary`, `.paths`, …)
- closure parameters (`{ operation -> … }`), `api` / `rule` / `it`

It is highlighting only — no parsing, diagnostics, or completion.

## Install — VS Code

**From the repo (recommended for contributors):**

```sh
ln -s "$(pwd)/editors/distill" ~/.vscode/extensions/distill-syntax
# then reload VS Code
```

**As a package:**

```sh
cd editors/distill
npx vsce package            # produces distill-syntax-0.1.0.vsix
code --install-extension distill-syntax-0.1.0.vsix
```

Applies to any file named `Matcher.dsl` (or `*.distill`). For a one-off file,
`Change Language Mode` → `Distill`.

## Install — IntelliJ IDEA / other JetBrains IDEs

Settings → Editor → **TextMate Bundles** → `+` → select this `editors/distill`
folder → Apply. JetBrains IDEs read the VS Code extension layout directly.

`Matcher.dsl` is then highlighted automatically; for `*.distill` add the
mapping under Settings → Editor → File Types if needed.

## Keeping the grammar current

The `function-name` and `member-name` alternations in
`distill.tmLanguage.json` are generated from `DistillMatcherEvaluator`'s
`KNOWN_FUNCTIONS` / `KNOWN_MEMBERS`. `DistillGrammarSnapshotTest` fails if they
drift. After adding a builtin:

```sh
mvn -pl arete-policy-plugin test -Dtest=DistillGrammarSnapshotTest -Dsnapshot.update=true
```

and commit the regenerated grammar.
