# Maven and Gradle plugins

The plugins run the engine **in the build**: no Areté service, no network call to one. Each only maps its
configuration onto the engine and acts on the result, so a build, the [command line](cli.md) and any other embedder judge
a spec the same way. Both write `gate.json`, `gate.md` and `gate.sarif` for a later CI step to post; Areté never calls
GitLab or GitHub.

The gate needs `git` on the path (it reads the base with `git show`); see the [gate](cli.md#the-merge-gate) for how it
judges a change, for shallow clones, and for the raw base source.

## Maven

```xml
<plugin>
  <groupId>net.dublinux.arete</groupId>
  <artifactId>arete-maven-plugin</artifactId>
  <version>0.1.0</version>
  <configuration>
    <target>origin/main</target>
    <policySources>
      <policySource>classpath:api-policy</policySource>
      <policySource>maven:org.acme:api-policy:2.3.1#sha256=9f2c…</policySource>
    </policySources>
    <requirePin>true</requirePin>
  </configuration>
</plugin>
```

```bash
mvn net.dublinux.arete:arete-maven-plugin:gate -Darete.target=origin/main
```

| Goal | What it does |
|---|---|
| `arete:gate` | The merge-gate. Runs once, at the directory Maven was started in, and fails the build on a regression. Defaults to the `verify` phase. |
| `arete:score` | Scores `specs` and fails under `failUnder` (a number, or `policy` for the policy's pass mark). |

Parameters (each also a property such as `-Darete.target`): `target`, `baseSha`, `paths`, `policy`, `policySources`,
`mavenRepositories`, `requirePin`, `cacheDir`, `userPolicies`, `rawUrl` and `rawHeaders` (the base from the code host, for a
shallow clone), `reportDirectory` (default `target/arete`), `reportOnly`, `skip`.

**`settings.xml` is read for you.** `useMavenSettings` (on by default) reads the settings files the build was started with,
so `mvn -s ci-settings.xml` and the active profiles apply: the repositories of `maven:` policy sources, mirrors,
credentials from `<servers>` and proxies come from there, as they do for the rest of CI. See
[policy sources](scoring/policy-engine.md#policy-sources) for what is used and what is not (encrypted passwords).

## Gradle

The plugin is published to Maven Central, not the Gradle Plugin Portal, so tell Gradle where to find it:

```groovy
// settings.gradle
pluginManagement {
    repositories { mavenCentral(); gradlePluginPortal() }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == 'net.dublinux.arete') useModule("net.dublinux.arete:arete-gradle-plugin:${requested.version}")
        }
    }
}
```

```groovy
// build.gradle
plugins { id 'net.dublinux.arete' version '0.1.0' }

arete {
    target = 'origin/main'
    policySources = ['classpath:api-policy', 'maven:org.acme:api-policy:2.3.1#sha256=9f2c…']
    mavenRepositories = ['https://repo.acme.com/maven']
    mavenSettings = file('ci/settings.xml')     // optional: or useMavenSettings = true for ~/.m2/settings.xml
    requirePin = true
    specs.from('apis/orders/openapi.yaml')       // for areteScore
}
```

| Task | What it does |
|---|---|
| `areteGate` | The merge-gate; fails the build on a regression. Never up to date. |
| `areteScore` | Scores `specs` and fails under `failUnder`. |

Neither is wired into `check`: run the gate as its own CI job so it can be a required check. Reports go to
`build/reports/arete` (`reportDirectory`). `reportOnly = true` writes them and never fails.

The plugin jar bundles the engine's libraries and moves them aside (Jackson, Guava, SnakeYAML and the rest are relocated
under `net.dublinux.arete.shaded`), because a Gradle plugin shares a classpath with every other build plugin. It uses
Gradle's own SLF4J.
