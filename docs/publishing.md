# Publishing to Maven Central

Four artifacts are published, because a build or an embedder depends on them:

| Artifact | For |
|---|---|
| `net.dublinux.arete:arete-engine-api` | The engine's public types (findings, scores, severities, spec input). |
| `net.dublinux.arete:arete-engine` | The embeddable policy engine. |
| `net.dublinux.arete:arete-maven-plugin` | `arete:gate` and `arete:score` in a Maven build. |
| `net.dublinux.arete:arete-gradle-plugin` | `areteGate` and `areteScore` in a Gradle build (a self-contained jar). |

The `arete-parent` pom they inherit from is uploaded with them. The command line jar and the local UI app are not on
Central: they are GitHub release assets (see [Releasing](releasing.md)). The old `arete-ci-gate-*` artifacts are retired.

The previous coordinates, `net.dublinux.speculate:speculate-scoring-spi`, are a different Maven artifact. Maven Central
does not rename artifacts, so existing consumers must migrate explicitly.

## One-time setup

1. Create or sign in to an account at the [Central Portal](https://central.sonatype.com/).
2. Claim and verify the `net.dublinux.arete` namespace. Use the repository's
   GitHub identity and follow the verification instructions shown by Central.
   Publishing will fail until this namespace is associated with the account.
3. Create a Central Portal user token. Store the token's username and password
   as GitHub Actions secrets named `MAVEN_CENTRAL_USERNAME` and
   `MAVEN_CENTRAL_PASSWORD`.
4. Create a GPG key for signing releases. Publish its public key to a public
   keyserver and keep the private key and passphrase safe.
5. Add the following GitHub Actions secrets to the repository:

   - `MAVEN_CENTRAL_USERNAME`
   - `MAVEN_CENTRAL_PASSWORD`
   - `GPG_PRIVATE_KEY` — the ASCII-armoured private key
   - `GPG_PASSPHRASE`

The `release` profile in the parent
[`pom.xml`](https://github.com/johnjoeallen/arete/blob/main/pom.xml)
creates the sources and Javadoc jars, signs all artifacts, and uploads them
through the Central Publishing Portal.

## Prepare a release

1. Make sure the working tree is clean.
2. Run the tests and package check locally:

   ```bash
   mvn -q verify
   ```

3. Commit the release-ready changes.
4. Create a new semantic version tag. Do not reuse a tag that has already been
   pushed:

   ```bash
   git tag vX.Y.Z
   git push origin main vX.Y.Z
   ```

The tag triggers the application release workflow, but it does not publish to
Central automatically.

## Publish

1. Open the repository's **Actions** tab.
2. Select **Publish to Maven Central**.
3. Choose **Run workflow** and enter the tag, for example `vX.Y.Z`.
4. Wait for the workflow to finish. It checks out that tag, derives the Maven
   version by removing the leading `v`, runs the tests, imports the GPG key, and runs:

   ```bash
   mvn --no-transfer-progress -Prelease -DskipTests \
     -pl .,arete-engine-api,arete-engine,arete-maven-plugin,arete-gradle-plugin deploy
   ```

5. Open [Central Portal deployments](https://central.sonatype.com/publishing/deployments),
   inspect the pending deployment, and publish it manually. The workflow has
   `autoPublish` disabled deliberately.
6. After Central finishes processing, verify the artifacts and their sources and
   Javadoc jars under the new coordinates.

## Migrating consumers

Replace the old dependency:

```xml
<groupId>net.dublinux.speculate</groupId>
<artifactId>speculate-scoring-spi</artifactId>
```

with:

```xml
<groupId>net.dublinux.arete</groupId>
<artifactId>arete-engine-api</artifactId>
```

Consumers that import SPI classes must also change Java package imports from
`net.dublinux.speculate...` to `net.dublinux.arete...`. Existing releases under
the old coordinates remain available only if they were published previously;
the new artifact does not replace them.

## If publishing fails

- **Namespace error:** verify that `net.dublinux.arete` is claimed and
  verified in Central Portal.
- **401/403 authentication error:** check the two Central Portal secrets and
  ensure they contain the generated user-token credentials, not the account
  password.
- **Signature error:** check that the private GPG key and passphrase secrets
  match, and that the public key is available from a public keyserver.
- **Version already exists:** choose a new version. Published Maven versions
  cannot be overwritten.
- **Wrong artifact:** confirm that the workflow was run with the intended tag;
  the tag determines the version and source contents.
