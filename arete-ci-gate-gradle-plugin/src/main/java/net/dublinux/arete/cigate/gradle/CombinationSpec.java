package net.dublinux.arete.cigate.gradle;

import net.dublinux.arete.cigate.Combination;

import java.io.Serializable;

/**
 * One {@code combination("validator/policy")} entry in the {@code areteCiGate}
 * block. Serializable and value-based so Gradle can treat it as a task input.
 */
public class CombinationSpec implements Serializable {

    private final String run;
    private boolean optional;

    public CombinationSpec(String run) {
        this.run = run;
    }

    public String getRun() {
        return run;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public Combination toCombination() {
        return Combination.parse(run, optional);
    }

    @Override
    public String toString() {
        return run + (optional ? " (optional)" : "");
    }
}
