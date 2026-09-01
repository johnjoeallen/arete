# arete-ci-gate-maven-plugin

Fails `mvn verify` when the module's OpenAPI spec fails its Areté policy. All
scoring happens on the Areté server — this plugin only submits the spec, reads
each `validator/policy` verdict, and ANDs the non-optional ones.

See [`design-notes/build-scoring-plugins.md`](../design-notes/build-scoring-plugins.md)
for the full design.

## Usage

```xml
<plugin>
  <groupId>net.dublinux.arete</groupId>
  <artifactId>arete-ci-gate-maven-plugin</artifactId>
  <version>1.0.0</version>
  <executions>
    <execution><goals><goal>check</goal></goals></execution>
  </executions>
  <configuration>
    <areteUrl>${arete.url}</areteUrl>                <!-- default http://localhost:6809 -->
    <spec>src/main/resources/openapi.yaml</spec>
    <combinations>
      <combination><run>generic-policy/Enterprise Grade</run></combination>
      <combination>
        <run>generic-policy/Zalando</run>
        <optional>true</optional>                    <!-- runs, reported, not gating -->
      </combination>
    </combinations>
  </configuration>
</plugin>
```

Then two profiles pick the Areté URL:

```xml
<profiles>
  <profile>
    <id>arete-local</id>
    <activation><activeByDefault>true</activeByDefault></activation>
    <properties><arete.url>http://localhost:6809</arete.url></properties>
  </profile>
  <profile>
    <id>arete-ci</id>
    <activation><property><name>env.CI</name></property></activation>
    <properties><arete.url>https://arete.internal.example.com</arete.url></properties>
  </profile>
</profiles>
```

## Configuration

| Parameter | Property | Default | Notes |
|---|---|---|---|
| `areteUrl` | `arete.url` | `http://localhost:6809` | Base URL of the Areté instance. |
| `namespace` | `arete.namespace` | `${project.groupId}` | Submission namespace. |
| `submitter` | `arete.submitter` | CI actor var, else `maven` | Attribution label, not auth. |
| `spec` | `arete.spec` | `src/main/resources/openapi.yaml` | The spec file. |
| `specs` | — | — | Extra spec files; each runs every combination. |
| `combinations` | — | *(required)* | `<combination>` list of `<run>` + `<optional>`. |
| `sarif` | `arete.sarif` | `false` | Also write `target/arete-ci-gate/<spec>.sarif`. |
| `failOn` | `arete.failOn` | *(unset)* | Advanced: `error` \| `blocker` \| `score<NN`, a stricter bar than the policy. |
| `failOnUnavailable` | `arete.failOnUnavailable` | `true` | If `false`, an unreachable Areté is a warning, not a failure. |
| `skip` | `arete.gate.skip` | `false` | Skip the goal. |

## Exit behaviour

- All non-optional combinations pass → build passes.
- A non-optional combination fails its policy → `MojoFailureException` (a normal
  build failure).
- Areté unreachable / request rejected / server error → `MojoExecutionException`
  (a build error, never reported as a scoring failure).
