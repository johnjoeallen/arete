package net.dublinux.arete.plugin;

import org.junit.jupiter.api.Test;
import net.dublinux.arete.validation.spi.Severity;
import net.dublinux.arete.validation.spi.Diagnostic;

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
        Diagnostic endpointError = Diagnostic.builder()
                .ruleId("r1").title("t1").severity(Severity.ERROR).pointer("/paths/~1items/get").build();
        Diagnostic sameEndpointWarning = Diagnostic.builder()
                .ruleId("r2").title("t2").severity(Severity.WARNING)
                .pointer("/paths/~1items/get/responses/200").build();
        Diagnostic otherEndpointHint = Diagnostic.builder()
                .ruleId("r3").title("t3").severity(Severity.HINT).pointer("/paths/~1items/post").build();
        Diagnostic specLevel = Diagnostic.builder()
                .ruleId("r4").title("t4").severity(Severity.ERROR).pointer("/info").build();

        AttributedDiagnostic avEndpointError = new AttributedDiagnostic("p", "P", endpointError);
        AttributedDiagnostic avSameEndpointWarning = new AttributedDiagnostic("p", "P", sameEndpointWarning);
        AttributedDiagnostic avOtherEndpointHint = new AttributedDiagnostic("p", "P", otherEndpointHint);
        AttributedDiagnostic avSpecLevel = new AttributedDiagnostic("p", "P", specLevel);

        List<AttributedDiagnostic> diagnostics =
                List.of(avEndpointError, avSameEndpointWarning, avOtherEndpointHint, avSpecLevel);

        Map<String, EndpointFindingsView> byEndpoint = EndpointFindings.byEndpoint(diagnostics);

        assertThat(byEndpoint).containsOnlyKeys("GET /items", "POST /items");
        EndpointFindingsView getFindings = byEndpoint.get("GET /items");
        assertThat(getFindings.counts().errorCount()).isEqualTo(1);
        assertThat(getFindings.counts().warningCount()).isEqualTo(1);
        assertThat(getFindings.counts().infoCount()).isEqualTo(0);
        assertThat(getFindings.counts().hintCount()).isEqualTo(0);
        assertThat(getFindings.counts().total()).isEqualTo(2);
        assertThat(getFindings.diagnostics()).containsExactly(avEndpointError, avSameEndpointWarning);

        EndpointFindingsView postFindings = byEndpoint.get("POST /items");
        assertThat(postFindings.counts().hintCount()).isEqualTo(1);
        assertThat(postFindings.counts().total()).isEqualTo(1);
        assertThat(postFindings.diagnostics()).containsExactly(avOtherEndpointHint);
    }

    @Test
    void byEndpointReturnsAnEmptyMapForNoDiagnostics() {
        assertThat(EndpointFindings.byEndpoint(List.of())).isEmpty();
    }

    @Test
    void byEndpointAttributesADiagnosticToEveryEndpointItsPathsListNamesInAdditionToItsPointer() {
        Diagnostic crossEndpointRule = Diagnostic.builder()
                .ruleId("r1").title("t1").severity(Severity.ERROR)
                .pointer("/paths/~1items/get")
                .paths(List.of("GET /items", "POST /items", "DELETE /items/{id}"))
                .build();

        AttributedDiagnostic av = new AttributedDiagnostic("p", "P", crossEndpointRule);
        Map<String, EndpointFindingsView> byEndpoint = EndpointFindings.byEndpoint(List.of(av));

        assertThat(byEndpoint).containsOnlyKeys("GET /items", "POST /items", "DELETE /items/{id}");
        assertThat(byEndpoint.get("GET /items").diagnostics()).containsExactly(av);
        assertThat(byEndpoint.get("POST /items").diagnostics()).containsExactly(av);
        assertThat(byEndpoint.get("DELETE /items/{id}").diagnostics()).containsExactly(av);
    }

    @Test
    void byEndpointDoesNotDuplicateADiagnosticWhenItsPathsListRepeatsItsPointersOwnEndpoint() {
        Diagnostic diagnostic = Diagnostic.builder()
                .ruleId("r1").title("t1").severity(Severity.ERROR)
                .pointer("/paths/~1items/get")
                .paths(List.of("GET /items"))
                .build();

        AttributedDiagnostic av = new AttributedDiagnostic("p", "P", diagnostic);
        Map<String, EndpointFindingsView> byEndpoint = EndpointFindings.byEndpoint(List.of(av));

        assertThat(byEndpoint).containsOnlyKeys("GET /items");
        assertThat(byEndpoint.get("GET /items").diagnostics()).containsExactly(av);
    }

    @Test
    void byEndpointNormalizesALowercaseMethodInAPathsEntry() {
        Diagnostic diagnostic = Diagnostic.builder()
                .ruleId("r1").title("t1").severity(Severity.ERROR)
                .paths(List.of("get /items"))
                .build();

        AttributedDiagnostic av = new AttributedDiagnostic("p", "P", diagnostic);
        Map<String, EndpointFindingsView> byEndpoint = EndpointFindings.byEndpoint(List.of(av));

        assertThat(byEndpoint).containsOnlyKeys("GET /items");
    }
}
