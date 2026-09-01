package net.dublinux.arete.cigate;

import java.util.List;
import java.util.function.Function;

/**
 * Resolves the {@code submitter} label. An explicit value always wins;
 * otherwise a CI actor variable is preferred over the build-tool default, so
 * a spec submitted from CI is attributed to the person who triggered it.
 */
public final class Submitters {

    /** CI actor env vars, most-common first. */
    static final List<String> CI_ACTOR_VARS = List.of(
            "GITHUB_ACTOR", "GITLAB_USER_LOGIN", "BUILD_USER_ID", "BITBUCKET_STEP_TRIGGERER_UUID");

    private Submitters() {
    }

    public static String resolve(String explicit, String buildToolDefault, Function<String, String> env) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        for (String var : CI_ACTOR_VARS) {
            String value = env.apply(var);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return buildToolDefault;
    }
}
