# Plan — Sandboxing rule scripts

Status: **active — gates re-enabling the Groovy runtime** · Target module:
`policy-based-validation-plugin`

> **Where this stands.** The default rule runtime is now Starlark, which is
> safe by construction — see
> [`policy-engine-dsl-research.md`](policy-engine-dsl-research.md) and
> [`policy-engine-dsl-poc.md`](policy-engine-dsl-poc.md). The Groovy runtime is
> still supported but **disabled by default** because it is unsandboxed. This
> plan is what makes it safe to turn back on:
>
> - **Layers A + B** (compile-time gate + runtime interceptor) are the
>   prerequisite for re-enabling Groovy as a first-class option.
> - **Layer C (§9)** and **§10 (supply-chain)** additionally gate loading
>   *remote* bundles, in either language — a pure interpreter with step caps is
>   a smaller RCE target than Groovy, but it is still in-process.

## 1. Problem

`GroovyRuleRuntime` today runs bundle-supplied Groovy with a bare
`GroovyShell`:

```java
new GroovyShell().parse(rule.source());        // validate()
new GroovyShell().evaluate(rule.source());      // execute() — re-parsed every call
```

A rule script has the full authority of the plugin JVM: filesystem,
network, `System.exit`, reflection, thread creation, `String.execute()`,
`@Grab`, unbounded loops.

### Threat model — bundles are becoming untrusted

Two changes make this a real security boundary, not a nicety:

1. **Developer-supplied bundles.** Devs can already drop rule/policy/rule
   files under `~/.arete/…`. Those rules are not reviewed by us and
   run with full JVM authority today — "drop a bundle" is as dangerous as
   "drop a jar".
2. **Remote bundle loading (planned).** Bundles fetched over the network are
   attacker-controlled by definition: a MITM, a compromised host, or a
   malicious publisher can ship a rule that reads `~/.ssh`, exfiltrates
   over HTTP, or mines crypto.

So the design target is now: **a rule is hostile code.** Its only
legitimate job is total — *read the `api` map and the `rule` map, return a
`List<Map>` of diagnostics* — and it needs no ambient capability. The runtime
must enforce exactly that, and also survive a rule that is trying to
break out or burn resources.

A buggy (not even malicious) rule — infinite loop, allocation bomb —
must also fail its own rule, not the whole run.

## 2. Goals / non-goals

**Goals**

1. A rule can touch only: the two input maps, JDK value types
   (`String`, numbers, `Boolean`), collections, regex, `Math`. Nothing else.
2. Diagnostics are caught **at bundle load** where statically detectable, and
   **at execution** otherwise — never silently.
3. Bounded execution: wall-clock timeout and output caps; a runaway rule
   fails its own rule, not the run.
4. Every one of the 17 bundled rules keeps producing identical results
   (existing tests are the oracle).
5. A clear, reviewed allowlist that a maintainer extends deliberately.

6. **Contain resource abuse**, not just capability abuse: a rule cannot
   OOM the host, spin a core indefinitely, or fill the disk.
7. **Verify provenance** of remote bundles before a single line is compiled
   (§10).

**Non-goals**

- Sandboxing the `zally-validation-plugin` (separate, Kotlin, out of scope).
- A script-approval UI à la Jenkins. We want default-deny, not
  approve-on-prompt.
- Defending against a kernel/JVM 0-day from inside the same machine — the
  out-of-process tier (§9) reduces but does not eliminate this; a bundle you
  have no trust signal for at all should simply not be loaded.

> **Alternative under evaluation.** Instead of hobbling Groovy, the rule
> language could be replaced with one that is *safe by construction* (Starlark
> or a small purpose-built language) — no I/O or reflection to sandbox because
> the interpreter cannot express them. That would remove Layers A + B below
> and likely soften Layer C. See
> [`policy-engine-dsl-research.md`](policy-engine-dsl-research.md); decide
> before starting phase 1.

## 3. Approach

Two layers, mirroring Jenkins' `script-security` but strict-by-default:

### Layer A — compile-time gate (`SecureASTCustomizer` + import control)

A shared, immutable `CompilerConfiguration` used for **both** `validate()`
and `execute()`:

- `ImportCustomizer`: no implicit extra imports.
- `SecureASTCustomizer`:
  - `setPackageAllowed(false)`
  - `setIndirectImportCheckEnabled(true)` (blocks fully-qualified
    `java.lang.System.exit(...)` dodges)
  - `importsWhitelist` / `starImportsWhitelist` → empty, or a tiny set
    (`java.util`, `java.util.regex`). Everything else is a compile error.
  - `setMethodDefinitionAllowed(false)` — rules are a single closure
    expression; no top-level `def foo() {}`.
  - `setClosuresAllowed(true)`
  - receivers/constant-types blacklist: `System`, `Runtime`, `Thread`,
    `GroovySystem`, `Eval`, `ClassLoader`, `File`, `ProcessBuilder`,
    `ProcessGroovyMethods`.
- Custom AST check: reject any annotation usage in the script
  (`@Grab`, `@ASTTest`, `@AnnotationCollector`, …) — rules need none.
- `ASTTransformationCustomizer(TimedInterrupt, value: <timeoutMs>, unit:
  MILLISECONDS)` — injects time checks into every loop and method entry so a
  `while (true) {}` throws `TimeoutException` on its own.

`SecureASTCustomizer` is **static analysis only** — it cannot see
`"cmd".execute()`, `foo.getClass().forName(...)`, GString-driven dispatch, or
metaclass tricks. That is Layer B's job.

### Layer B — runtime interceptor (`groovy-sandbox`)

Add `org.kohsuke:groovy-sandbox` (Jenkins-maintained; **verify the version
that supports Groovy 4.0.x — spike #1**). It adds a `SandboxTransformer`
compilation customizer that routes **every** method call, constructor,
static call, property get/set, and attribute access through a
`GroovyInterceptor`.

Implement `AreteRuleSandbox extends GroovyInterceptor`:

- **`onNewInstance`** — allow only: `ArrayList`, `LinkedList`, `HashMap`,
  `LinkedHashMap`, `HashSet`, `LinkedHashSet`, `StringBuilder`,
  `java.util.regex.Pattern` (via `compile`). Deny all else.
- **`onStaticCall`** — allow `Math.*`, `Pattern.compile`, `Integer.parseInt`,
  `Long.parseLong`, `Double.parseDouble`. Deny all else.
- **`onMethodCall` / `onGroovyCall`** — allow when the receiver is an
  instance of an **allowlisted type**:
  `Map`, `Map.Entry`, `Collection`, `List`, `Set`, `Iterator`, `Iterable`,
  `CharSequence`/`String`/`GString`, `StringBuilder`, `Number`, `Boolean`,
  `Character`, `Pattern`, `Matcher`, `Closure`, `Range`, `Comparable`,
  `Enum`. Covers every GDK helper the rules use (`collect`,
  `collectMany`, `findAll`, `any`, `every`, `count`, `unique`, `group`,
  `leftShift`, `toLowerCase`, `endsWith`, `split`, `trim`, `matches`, `==~`,
  `=~`).
- **Method-name denylist, applied regardless of receiver** (these are GDK
  methods on *allowed* types that escape the box):
  `execute`, `evaluate`, `getClass`, `getMetaClass`, `setMetaClass`,
  `invokeMethod`, `newInstance`, `sleep`, `wait`, `notify`, `notifyAll`,
  `toURL`, `toURI`, `newInputStream`, `withReader`, `eachLine`,
  `getText`, `readLines`, `mixin`, `with`? (allow `with`), `identity`.
- **`onGetProperty`** — deny `class`, `metaClass`, `binding`, `properties`;
  allow map-key / bean-style reads on allowlisted types.
- **`onSetProperty` / `onSetAttribute` / `onGetAttribute`** — deny outright
  (no `.@field`, no mutation of `api`). Rules build *new* structures.
- **`onSuperCall` / `onSuperCall`** — deny.

The interceptor is registered per-thread immediately around
`closure.call(api, rule)` and unregistered in a `finally`. Enforcement =
"a sandbox is registered on this thread"; scripts compiled with the
transformer but run without a registered interceptor would be unguarded, so
the runtime must never call a rule outside the try/finally.

### Why both, and why Layer A is not enough on its own

Layer A is **not a security boundary** — it is static analysis. It fails the
*bundle* fast with a readable message for the statically visible abuse (bad
import, `@Grab`, a bare `System.exit`), which is valuable, but it cannot see
Groovy's dynamic dispatch. All of the following need no import and no new
class literal, so Layer A misses every one:

```groovy
'id'.execute().text                                  // GDK on String → spawns a process
api.getClass().forName('java.lang.Runtime')           // getClass() is the master key
   .getRuntime().exec('...')
api.class.classLoader.loadClass('java.lang.System')
"${api.class.forName('java.lang.System').exit(0)}"    // dispatch hidden in a GString
new long[1 << 30]                                     // allocation bomb
```

There is no `SecureASTCustomizer` switch for `.class` / `getClass()`, and a
hand-written static blocker loses to computed names
(`api."${'getCl'+'ass'}"()`) and indirection (`def x=[api]; x[0].getClass()`).
This is exactly the history behind ~30 Jenkins script-security CVEs.

**Layer B (runtime interception) is therefore mandatory**, not optional.
Layer A + code review would only be defensible if bundles stayed
trusted-by-build — and per §1 they do not. Layer C (§9) is additionally
required before remote loading ships.

## 4. Execution model changes (`GroovyRuleRuntime`)

Current `execute()` re-parses the script on every call. Fix as part of this
work:

1. `validate(Rule)` compiles the source **once** with the secure config,
   caches the resulting `Class<Script>` keyed by rule id.
2. `execute(Rule, api, rule)`:
   - get cached class → `InvokerHelper.createScript(clazz, new Binding())`
     → `run()` → expect a `Closure`;
   - `GroovyInterceptor sandbox = new AreteRuleSandbox();
     sandbox.register();`
   - submit `closure.call(api, rule)` to a bounded single-thread
     `ExecutorService`; `future.get(timeoutMs, MS)`; `future.cancel(true)` +
     unregister on timeout.
   - `finally { sandbox.unregister(); }`
3. Wrap the whole thing in `catch (Throwable)` (currently
   `RuntimeException`) so `StackOverflowError`, `TimeoutException`,
   `SecurityException` from the interceptor, and sandbox
   `RejectedAccessException` all become `RuleException` →
   `ValidationResult.pluginError` for that rule. Keep the existing
   ">1000 diagnostics" and "non-blank message" checks.

Thread-safety: the cached `Class` is immutable; a fresh `Script`/`Closure`
and a fresh interceptor per call keeps `validate()` concurrency intact. The
executor can be a small shared pool.

Config: `timeoutMs`, `maxDiagnostics` (already 1000), `maxMessageLength`
from a `SandboxLimits` record; overridable via `configure(Map)` /
system property for ops, with sane defaults (e.g. 2000 ms).

## 5. The one JDK constructor in the bundle: `new URI(url)`

`rules/hostname/Matcher.groovy` is the only script that constructs a JDK
object — `new URI(url).host`, to get the host out of a declared server URL.
Everything else is closures, collections, strings and regex. Three ways to
deal with it, in preference order:

### 5a (preferred) — extract the host in the rule with string ops

No adapter change, no sandbox carve-out. `java.net.URI` parsing is overkill
for a heuristic naming check; a regex does it:

```groovy
def host = (url =~ /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\/([^\/?#:]+)/) ? Matcher.lastMatcher.group(1) : null
```

or a plain `url.replaceFirst(~'^\\w[\\w+.-]*://', '').split(~'[/?#:]')[0]`.
The rule stays entirely within the `Map`/`String`/regex allowlist. Update
the rule + its one test (`namingRuleInspects…`? — it's covered by the
`hostname` cases in `PolicyBasedValidationPluginTest`). Do this first; it
removes the only reason to touch anything outside the sandbox layers.

### 5b (fallback) — narrowly whitelist `java.net.URI`

If 5a proves too lossy in practice, allow **only** `new URI(String)` in
`onNewInstance` and `getHost` / `getScheme` / `getPort` / `getAuthority` in
`onMethodCall`. This is safe: `java.net.URI` is a pure value type and does no
I/O — network access lives on `java.net.URL#openConnection`, which stays
denied. Cost: one named exception in `SandboxPolicy` with a comment.

### 5c (last resort) — structured servers in `OpenApiMapAdapter`

`api.servers[] -> { url, host, scheme, port }`. Rejected unless 5a and 5b
both fail, because it grows the adapter's contract for one rule's
convenience.

## 6. Rollout

| Phase | Change | Kill-switch |
|-------|--------|-------------|
| 0 | Rewrite `hostname` rule host-extraction (§5a); refactor `execute()` to cache compiled classes (no behaviour change) | — |
| 1 | Land Layer A + Layer B in **audit mode**: log `WARN` on every access that *would* be denied, do not throw. Ship a release. | n/a (not enforcing) |
| 2 | Flip to **enforce** (Layers A+B). `-Darete.policy.sandbox=audit` (or `off`) as a documented, discouraged escape hatch for one release. | yes |
| 3 | Remove the escape hatch; `off` no longer honoured. Local dev bundles now run sandboxed. | no |
| 4 | Layer C (§9): out-of-process worker + OS containment. | — |
| 5 | Supply-chain verification (§10): signature check, pinning, explicit remote-source config. **Gate: remote loading does not ship before 4 + 5.** | — |

Audit mode gives one release where a broken allowlist shows up as logs, not
as every Zalando policy returning `pluginError`. Phases 0–3 are the
local-bundle boundary and can ship independently; 4–5 are prerequisites for
the remote-loading feature.

## 7. Testing

- **Regression**: existing `PolicyBasedValidationPluginTest` /
  `...LoadIT` already assert exact diagnostic counts and scores for every
  bundled rule — they must pass unchanged with the sandbox enforcing.
- **Attack suite** (new `RuleSandboxTest`): a parameterised list, each
  expected to fail (compile-time *or* `RuleException` at run):
  - `System.exit(0)` / `java.lang.System.exit(0)`
  - `Runtime.runtime.exec('id')`
  - `'id'.execute().text`
  - `new File('/etc/passwd').text`
  - `new URL('http://169.254.169.254/').text`
  - `Thread.start { }` / `new Thread().start()`
  - `Eval.me('1+1')` / `new GroovyShell().evaluate('1')`
  - `this.class.classLoader.loadClass('java.lang.System')`
  - `Class.forName('java.lang.Runtime')`
  - `api.metaClass` / `"x".metaClass.foo = {}`
  - `"${System.exit(0)}"` (GString dispatch)
  - `@Grab('x:y:1')` header
  - `while (true) { }`  → `TimeoutException`
  - deep unbounded recursion → `StackOverflowError` → `RuleException`
  - `api.clear()` / `api.paths << [:]` (input mutation) → denied
  - returning 5000 diagnostics → existing cap
- **Load-time**: a rule with a disallowed import fails
  `PolicyBundleLoader.load` with a message naming the construct.
- **Allowlist completeness**: a test that compiles + runs every
  `rules/*/Matcher.groovy` against a representative spec with the
  sandbox enforcing and asserts no `RejectedAccessException` — this is the
  guard that stops a future rule needing a silent allowlist bump.

## 8. Docs / comms

- Replace the "Coming change" callout in `docs/policy-engine.md` with the
  real contract: the allowlisted types, the method-name denylist, the
  timeout, and "extending the allowlist is a reviewed change".
- README: drop "(a strict sandbox is planned)" → "runs in a strict
  sandbox".
- Changelog / release notes for phases 1–3.

## 9. Layer C — out-of-process isolation (required before remote loading)

In-JVM interception (Layer B) is a real boundary but shares a heap, a
process, and a filesystem view with the host. It cannot hard-cap memory or
CPU, and a Groovy/JDK gadget-chain 0-day escapes it entirely. Fine for
locally-authored dev bundles; **not** acceptable for network-fetched code.

Before remote loading ships, rule execution moves to a child process:

- A small worker JVM launched with `-Xmx<small>`, `-XX:ActiveProcessorCount=1`,
  a distinct temp `user.home`, and Layers A+B still applied inside it.
- Host containment around it: run under a container / `bwrap` with **no
  network namespace**, a read-only and near-empty FS, a memory cgroup, and a
  hard CPU/wall limit (`ulimit -t`, cgroup `cpu.max`). (`SecurityManager` is
  gone in modern JDKs, so isolation has to come from the OS, not the JVM.)
- Protocol: host sends `{rule source, api map, rule map}` as JSON/CBOR
  over a pipe; worker returns `{diagnostics}` or an error. The worker never
  sees other specs, the DB, or `~/.arete`.
- Worker pool with recycling; a worker that times out or dies is replaced,
  and its rule reports `pluginError`.

This is a phase, not an afterthought — it is on the critical path for the
remote-bundle feature.

## 10. Supply chain — trusting a remote bundle

Execution sandboxing is orthogonal to *whether you should run this bundle at
all*. Before a remote bundle is compiled:

- **Transport**: HTTPS only, modern TLS, no plaintext fallback.
- **Integrity + provenance**: the bundle is a signed archive; the host
  verifies a detached signature against a configured trusted key before
  reading any file. (The repo already ships `public-key.asc` and has GPG
  signing in the release workflow — reuse that trust root.)
- **Pinning / TOFU**: record the key + digest a source was first accepted
  with; a changed key is a hard failure needing explicit re-approval.
- **Explicit opt-in**: a remote source and its key are configured
  deliberately (settings / config file), never auto-discovered. No implicit
  "load from URL in the spec".
- **Bundle resource limits**: max archive size, max file count, max script
  length, max rules/rules/policies — enforced in `PolicyBundleLoader`
  before parsing. The YAML path is already hardened (`SafeConstructor`,
  `setMaxAliasesForCollections(20)`, `safePath` zip-slip guard); re-audit it
  as untrusted input.

## 11. Work breakdown

1. **Spike**: confirm `groovy-sandbox` version compatible with Groovy
   4.0.30; prototype `SandboxTransformer` + a 3-rule interceptor against the
   `naming` rule. (0.5–1 d)
2. Rewrite `hostname/Matcher.groovy` to extract the host with regex/string
   ops (§5a); update its test. No adapter change. (0.25 d)
3. `GroovyRuleRuntime`: compiled-class cache, executor + timeout,
   `catch (Throwable)`, `SandboxLimits`. (1 d)
4. Layer A: shared secure `CompilerConfiguration`, wire into `validate()` +
   compile cache; load-time tests. (1 d)
5. Layer B: `AreteRuleSandbox` interceptor + `SandboxPolicy`
   allowlist constants; audit-mode flag. (1.5–2 d)
6. `RuleSandboxTest` attack suite + allowlist-completeness test. (1 d)
7. Docs + README + release notes. (0.5 d)
8. Phase-2 flip + remove escape hatch in a later release. (0.25 d each)

_Rough estimate: ~7 engineering days to enforce-capable in audit mode for
local bundles (phases 0–3)._

### Later, gating remote loading

9. Layer C: worker-process protocol + pool; container/`bwrap` profile
   (no-net, ro-fs, cgroup limits); CI to prove the profile holds. (4–6 d)
10. Supply chain: signed-archive verification against the existing GPG trust
    root; source + key config; TOFU pinning; `PolicyBundleLoader` resource
    caps + untrusted-input re-audit. (3–5 d)

_These two are prerequisites for the remote-bundle feature and should be
scoped with it, not with the local sandbox._
