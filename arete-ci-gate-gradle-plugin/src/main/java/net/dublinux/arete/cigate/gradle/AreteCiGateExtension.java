package net.dublinux.arete.cigate.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.io.File;

/**
 * The {@code areteCiGate { }} configuration block.
 *
 * <pre>{@code
 * areteCiGate {
 *     url = "http://localhost:6809"
 *     namespace = project.group.toString()
 *     spec = file("src/main/resources/openapi.yaml")
 *     combination("generic-policy/Enterprise Grade")
 *     combination("generic-policy/Zalando") { it.optional = true }
 * }
 * }</pre>
 */
public abstract class AreteCiGateExtension {

    public abstract Property<String> getUrl();

    public abstract Property<String> getNamespace();

    public abstract Property<String> getSubmitter();

    public abstract Property<File> getSpec();

    public abstract ListProperty<File> getSpecs();

    public abstract ListProperty<CombinationSpec> getCombinations();

    public abstract Property<Boolean> getSarif();

    public abstract Property<String> getFailOn();

    public abstract Property<Boolean> getFailOnUnavailable();

    public void combination(String run) {
        getCombinations().add(new CombinationSpec(run));
    }

    public void combination(String run, org.gradle.api.Action<? super CombinationSpec> configure) {
        CombinationSpec spec = new CombinationSpec(run);
        configure.execute(spec);
        getCombinations().add(spec);
    }
}
