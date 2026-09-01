package net.dublinux.arete.cigate;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubmittersTest {

    @Test
    void explicitValueWins() {
        assertEquals("payments-ci",
                Submitters.resolve("payments-ci", "maven", Map.of("GITHUB_ACTOR", "octocat")::get));
    }

    @Test
    void aCiActorVariableIsPreferredOverTheBuildToolDefault() {
        assertEquals("octocat",
                Submitters.resolve(null, "maven", Map.of("GITHUB_ACTOR", "octocat")::get));
        assertEquals("jane",
                Submitters.resolve("  ", "gradle", Map.of("GITLAB_USER_LOGIN", "jane")::get));
    }

    @Test
    void fallsBackToTheBuildToolDefault() {
        assertEquals("maven", Submitters.resolve(null, "maven", Map.<String, String>of()::get));
    }
}
