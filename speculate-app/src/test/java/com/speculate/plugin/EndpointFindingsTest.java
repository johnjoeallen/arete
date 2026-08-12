package com.speculate.plugin;

import org.junit.jupiter.api.Test;
import speculate.validation.spi.Severity;
import speculate.validation.spi.Violation;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointFindingsTest {

    @Test
    void mapsAPointerAtAnOperationDirectlyToItsEndpointKey() {
        assertThat(EndpointFindings.endpointKey("/paths/~1items/get")).isEqualTo("GET /items");
    }

    @Test
    void mapsAPointerNestedDeeperUnderAnOperationToTheSameEndpointKey() {
        assertThat(EndpointFindings.endpointKey("/paths/~1items~1{id}/get/responses/200/content/application~1json/schema"))
                .isEqualTo("GET /items/{id}");
    }

    @Test
    void unescapesATildeInAPathSegmentInTheCorrectOrder() {
        // "~0" must be restored to "~" only *after* "~1" is restored to "/",
        // otherwise a literal "~1" in a path would wrongly become "/".
        assertThat(EndpointFindings.endpointKey("/paths/~1foo~0bar/post")).isEqualTo("POST /foo~bar");
    }

    @Test
    void normalizesTheMethodSegmentToUppercase() {
        assertThat(EndpointFindings.endpointKey("/paths/~1items/delete")).isEqualTo("DELETE /items");
    }

    @Test
    void nullForAPointerNotRootedUnderPaths() {
        assertThat(EndpointFindings.endpointKey("/info/title")).isNull();
        assertThat(EndpointFindings.endpointKey("")).isNull();
        assertThat(EndpointFindings.endpointKey(null)).isNull();
    }

    @Test
    void nullForAPointerAtAPathOrOperationLevelWithNoMethodSegment() {
        assertThat(EndpointFindings.endpointKey("/paths")).isNull();
        assertThat(EndpointFindings.endpointKey("/paths/~1items")).isNull();
    }

    @Test
    void nullWhenTheThirdSegmentIsNotAnHttpMethod() {
        assertThat(EndpointFindings.endpointKey("/paths/~1items/parameters/0")).isNull();
    }

    @Test
    void byEndpointCountsBySeverityAndExcludesUnattributableFindings() {
        Violation endpointError = Violation.builder()
                .ruleId("r1").title("t1").severity(Severity.ERROR).pointer("/paths/~1items/get").build();
        Violation sameEndpointWarning = Violation.builder()
                .ruleId("r2").title("t2").severity(Severity.WARNING)
                .pointer("/paths/~1items/get/responses/200").build();
        Violation otherEndpointHint = Violation.builder()
                .ruleId("r3").title("t3").severity(Severity.HINT).pointer("/paths/~1items/post").build();
        Violation specLevel = Violation.builder()
                .ruleId("r4").title("t4").severity(Severity.ERROR).pointer("/info").build();

        AttributedViolation avEndpointError = new AttributedViolation("p", "P", endpointError);
        AttributedViolation avSameEndpointWarning = new AttributedViolation("p", "P", sameEndpointWarning);
        AttributedViolation avOtherEndpointHint = new AttributedViolation("p", "P", otherEndpointHint);
        AttributedViolation avSpecLevel = new AttributedViolation("p", "P", specLevel);

        List<AttributedViolation> violations =
                List.of(avEndpointError, avSameEndpointWarning, avOtherEndpointHint, avSpecLevel);

        Map<String, EndpointFindingsView> byEndpoint = EndpointFindings.byEndpoint(violations);

        assertThat(byEndpoint).containsOnlyKeys("GET /items", "POST /items");
        EndpointFindingsView getFindings = byEndpoint.get("GET /items");
        assertThat(getFindings.counts().errorCount()).isEqualTo(1);
        assertThat(getFindings.counts().warningCount()).isEqualTo(1);
        assertThat(getFindings.counts().infoCount()).isEqualTo(0);
        assertThat(getFindings.counts().hintCount()).isEqualTo(0);
        assertThat(getFindings.counts().total()).isEqualTo(2);
        assertThat(getFindings.violations()).containsExactly(avEndpointError, avSameEndpointWarning);

        EndpointFindingsView postFindings = byEndpoint.get("POST /items");
        assertThat(postFindings.counts().hintCount()).isEqualTo(1);
        assertThat(postFindings.counts().total()).isEqualTo(1);
        assertThat(postFindings.violations()).containsExactly(avOtherEndpointHint);
    }

    @Test
    void byEndpointReturnsAnEmptyMapForNoViolations() {
        assertThat(EndpointFindings.byEndpoint(List.of())).isEmpty();
    }
}
