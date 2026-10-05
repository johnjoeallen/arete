package net.dublinux.arete.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code arete { }} block. These settings only say what to ask the engine; both tasks hand them to it unchanged.
 *
 * <pre>
 * arete {
 *     target = 'origin/main'
 *     policySources = ['classpath:api-policy', 'maven:org.acme:api-policy:2.3.1#sha256=...']
 *     mavenRepositories = ['https://repo.acme.com/maven']
 *     requirePin = true
 * }
 * </pre>
 */
public abstract class AreteExtension {
    /** The policy to score with. Default: the one the spec's {@code .arete.yaml} names, else the bundle's first. */
    public abstract Property<String> getPolicy();

    /** Policy bundles, layered in order; each may carry a {@code #sha256=...&version=...} pin. */
    public abstract ListProperty<String> getPolicySources();

    /** Maven-layout repositories for {@code maven:} policy sources. */
    public abstract ListProperty<String> getMavenRepositories();

    /** A Maven settings.xml for repositories, mirrors, credentials and proxies (e.g. the one CI uses). */
    public abstract RegularFileProperty getMavenSettings();

    /** Read {@code ~/.m2/settings.xml} (over the global one) when no {@link #getMavenSettings()} file is given. */
    public abstract Property<Boolean> getUseMavenSettings();

    /** Refuse a remote policy source with no {@code sha256} pin. */
    public abstract Property<Boolean> getRequirePin();

    /** Where fetched policy bundles are kept (default {@code ~/.arete/cache/policies}). */
    public abstract DirectoryProperty getCacheDir();

    /** A directory of extra {@code *.md} policies. */
    public abstract DirectoryProperty getUserPolicies();

    // ---- the gate ----

    /** The git repository to check; default the root project's directory. */
    public abstract DirectoryProperty getRepository();

    /** The branch the change merges into; the base is the merge-base of HEAD and it. Default {@code origin/main}. */
    public abstract Property<String> getTarget();

    /** The base commit itself (a CI variable), instead of the merge-base. */
    public abstract Property<String> getBaseSha();

    /** Globs for the spec files, relative to the repository. Default {@code **}{@code /openapi.yaml}, {@code .yml}, {@code .json}. */
    public abstract ListProperty<String> getPaths();

    /** Read the base over HTTPS from the code host (a shallow clone): a URL with {path} and {ref}. Needs {@link #getBaseSha()}. */
    public abstract Property<String> getRawUrl();

    /** Headers for {@link #getRawUrl()}, such as {@code PRIVATE-TOKEN}. */
    public abstract MapProperty<String, String> getRawHeaders();

    /** Write the reports and never fail the build: a trial before the gate is required. */
    public abstract Property<Boolean> getReportOnly();

    /** Where {@code gate.json}, {@code gate.md} and {@code gate.sarif} (and the score reports) are written. */
    public abstract DirectoryProperty getReportDirectory();

    // ---- score ----

    /** The spec files {@code areteScore} scores. */
    public abstract ConfigurableFileCollection getSpecs();

    /** A score the specs must reach, or {@code policy} for the policy's own pass mark. Omit to only report. */
    public abstract Property<String> getFailUnder();
}
