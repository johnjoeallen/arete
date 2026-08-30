# Publishing the SPI to Maven Central

The SPI is now published under these coordinates:

```xml
<dependency>
  <groupId>net.dublinux.arete</groupId>
  <artifactId>arete-validation-spi</artifactId>
  <version>VERSION</version>
</dependency>
```

The previous coordinates, `net.dublinux.speculate:speculate-validation-spi`,
are a different Maven artifact. Maven Central does not rename artifacts, so
existing consumers must migrate explicitly.

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

The `release` profile in
[`arete-validation-spi/pom.xml`](https://github.com/johnjoeallen/arete/blob/main/arete-validation-spi/pom.xml)
creates the sources and Javadoc jars, signs all artifacts, and uploads them
through the Central Publishing Portal.

## Prepare a release

1. Make sure the new coordinates are present in the SPI POM and that the
   working tree is clean.
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

The tag triggers the application release workflow, but it does not publish the
SPI automatically.

## Publish the SPI

1. Open the repository's **Actions** tab.
2. Select **Publish SPI to Maven Central**.
3. Choose **Run workflow** and enter the tag, for example `vX.Y.Z`.
4. Wait for the workflow to finish. It checks out that tag, derives the Maven
   version by removing the leading `v`, imports the GPG key, and runs:

   ```bash
   mvn --no-transfer-progress -pl arete-validation-spi -Prelease deploy
   ```

5. Open [Central Portal deployments](https://central.sonatype.com/publishing/deployments),
   inspect the pending deployment, and publish it manually. The workflow has
   `autoPublish` disabled deliberately.
6. After Central finishes processing, verify the artifact and its sources and
   Javadoc jars under the new coordinates.

## Migrating consumers

Replace the old dependency:

```xml
<groupId>net.dublinux.speculate</groupId>
<artifactId>speculate-validation-spi</artifactId>
```

with:

```xml
<groupId>net.dublinux.arete</groupId>
<artifactId>arete-validation-spi</artifactId>
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
