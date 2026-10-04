package net.dublinux.arete;

import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.SpecFormat;
import net.dublinux.arete.engine.api.SpecInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EngineConfigTest {
    private static final String SPEC = "openapi: 3.0.0\ninfo: { title: T, version: 1.0.0 }\npaths:\n  /m:\n    get:\n      responses: { '200': { description: OK } }\n";

    private static String firstDocumentationUrl(String publicUrl) {
        Engine engine = new EngineConfig().engine(publicUrl);
        ScoringResult result = engine.score(SpecInput.builder().content(SPEC).format(SpecFormat.OPENAPI3).policy("Enterprise Grade").build());
        return result.getDiagnostics().get(0).getDocumentationUrl();
    }

    @Test
    void findingsLinkToTheRuleDocumentationThisApplicationServes() {
        assertThat(firstDocumentationUrl("http://localhost:6819"))
                .matches("http://localhost:6819/engines/generic-policy/rules/[A-Z]+\\d+");
    }

    @Test
    void aTrailingSlashOnThePublicUrlIsIgnored() {
        assertThat(firstDocumentationUrl("https://arete.acme.com/"))
                .startsWith("https://arete.acme.com/engines/generic-policy/rules/");
    }
}
