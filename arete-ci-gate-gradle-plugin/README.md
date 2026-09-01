# arete-ci-gate-gradle-plugin

Fails `gradle check` when the project's OpenAPI spec fails its Areté policy.
All scoring happens on the Areté server — this plugin only submits the spec,
reads each `validator/policy` verdict, and ANDs the non-optional ones.

See [`design-notes/build-scoring-plugins.md`](../design-notes/build-scoring-plugins.md)
for the full design.

## Usage

```kotlin
plugins {
    id("net.dublinux.arete.ci-gate") version "1.0.0"
}

areteCiGate {
    url = providers.gradleProperty("arete.url").orElse("http://localhost:6809")
    spec = file("src/main/resources/openapi.yaml")

    combination("generic-policy/Enterprise Grade")            // gating
    combination("generic-policy/Zalando") { it.optional = true }
}
```

Consume the plugin from Maven Central:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

The plugin registers `areteCiGateCheck` and wires `check.dependsOn(areteCiGateCheck)`.
The spec file is a task input, so an unchanged spec skips the task.

## Configuration

| Property | Default | Notes |
|---|---|---|
| `url` | `arete.url` gradle property, else `http://localhost:6809` | Base URL of the Areté instance. |
| `namespace` | `project.group` | Submission namespace. |
| `submitter` | CI actor var, else `gradle` | Attribution label, not auth. |
| `spec` | *(required unless `specs` set)* | The spec file. |
| `specs` | — | Extra spec files; each runs every combination. |
| `combination(run) { optional = … }` | *(at least one required)* | A `validator/policy` pair. |
| `sarif` | `false` | Also write `build/reports/arete-ci-gate/<spec>.sarif`. |
| `failOn` | *(unset)* | Advanced: `error` \| `blocker` \| `score<NN`, a stricter bar than the policy. |
| `failOnUnavailable` | `true` | If `false`, an unreachable Areté is a warning, not a failure. |

Override the URL per environment with `-Parete.url=…`,
`ORG_GRADLE_PROJECT_arete.url`, or an entry in `~/.gradle/gradle.properties`.

## Exit behaviour

- All non-optional combinations pass → build passes.
- A non-optional combination fails its policy → `VerificationException` (Gradle's
  normal verification-failure path).
- Areté unreachable / request rejected / server error → `GradleException`
  (a build error, never reported as a scoring failure).
