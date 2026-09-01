package net.dublinux.arete.cigate;

import java.util.Objects;

/**
 * One {@code <validator>/<policy>} pair to run the spec against.
 *
 * <p>{@link #optional()} is the build's choice, not the server's: an optional
 * combination is still submitted and still appears in the report, but its
 * verdict is excluded from the build gate.
 */
public record Combination(String validator, String policy, boolean optional) {

    public Combination {
        validator = requireText(validator, "validator");
        policy = requireText(policy, "policy");
    }

    /** Gating combination (the common case). */
    public static Combination gating(String validator, String policy) {
        return new Combination(validator, policy, false);
    }

    /** Parses {@code "validator/policy"}; the policy may itself contain '/'. */
    public static Combination parse(String run, boolean optional) {
        Objects.requireNonNull(run, "run");
        int slash = run.indexOf('/');
        if (slash <= 0 || slash == run.length() - 1) {
            throw new IllegalArgumentException(
                    "run must be '<validator>/<policy>', got: " + run);
        }
        return new Combination(run.substring(0, slash), run.substring(slash + 1), optional);
    }

    /** The {@code ?run=} query value: {@code validator/policy}. */
    public String run() {
        return validator + "/" + policy;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
