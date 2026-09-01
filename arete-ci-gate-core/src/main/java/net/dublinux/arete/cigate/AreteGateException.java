package net.dublinux.arete.cigate;

/**
 * A problem that is <em>not</em> a scoring failure: Areté was unreachable, the
 * request was rejected (bad spec, unknown validator/policy, unknown namespace),
 * or the server errored. The build tool should surface this as a build error
 * (Maven {@code MojoExecutionException} / Gradle {@code GradleException}),
 * distinct from a spec that scored and failed its policy.
 */
public class AreteGateException extends Exception {

    private final Integer httpStatus;

    public AreteGateException(String message) {
        this(message, null, null);
    }

    public AreteGateException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public AreteGateException(String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** The HTTP status that triggered this, if any. */
    public Integer httpStatus() {
        return httpStatus;
    }
}
