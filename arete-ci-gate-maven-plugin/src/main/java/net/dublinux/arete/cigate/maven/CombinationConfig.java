package net.dublinux.arete.cigate.maven;

import net.dublinux.arete.cigate.Combination;

/**
 * A {@code <combination>} element in the plugin configuration.
 *
 * <pre>{@code
 * <combination>
 *   <run>generic-policy/Enterprise Grade</run>
 *   <optional>false</optional>
 * </combination>
 * }</pre>
 */
public class CombinationConfig {

    /** {@code <validator>/<policy>}. */
    private String run;

    /** Omitted ⇒ the combination gates the build. */
    private boolean optional;

    public Combination toCombination() {
        return Combination.parse(run, optional);
    }

    public String getRun() {
        return run;
    }

    public void setRun(String run) {
        this.run = run;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }
}
