package com.speculate.validation.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every bundled detector that also ships a Detector.sift must produce exactly
 * the same occurrences as its Detector.star for a representative spec.
 */
class SiftParityTest {

    private static final String CATALOGUE_SPEC = """
            openapi: 3.0.0
            info: { title: Catalogue API, version: 1.0.0, openapi: 3.0.0 }
            servers: [ { url: https://api.example.com/v1 } ]
            paths:
              /orders:
                post:
                  responses:
                    '200':
                      description: OK
                      headers:
                        X-Trace: { description: trace id }
                        X-Count: { description: total, schema: { type: integer } }
                delete:
                  requestBody: { content: { application/json: { schema: { type: object } } } }
                  responses: { '204': { description: gone } }
              /orders/{orderId}:
                get:
                  parameters:
                    - { name: orderId, in: path, required: true, schema: { type: string } }
                    - { name: api_key, in: query, schema: { type: string } }
                    - { name: X-SSN, in: header, schema: { type: string } }
                  responses:
                    '200':
                      description: OK
                      content:
                        text/xml: { schema: { type: string } }
                        '*/*': { schema: { type: string } }
            components:
              schemas:
                CreateOrderRequest:
                  type: object
                  properties:
                    id: { type: integer }
                    created: { type: string }
                    expiresAt: { type: string, format: date-time }
                    startTime: { type: string, format: date-time }
                    password: { type: string }
                order_response:
                  type: object
                  properties:
                    modified: { type: string, format: date-time }
            """;

    @Test void manual() { assertParity("manual", "operation", Map.of()); }

    @Test void compatibility() { assertParity("compatibility", "api", Map.of("change", "interface-removed")); }

    @Test void dateTimeName() { assertParity("date-time-name", "property", Map.of("suffix", "_at"), true); }

    @Test void commonField() { assertParity("common-field", "property", Map.of("convention", "default"), true); }

    @Test void schemaNamePlaceholder() {
        assertParity("schema-name", "schema",
                Map.of("pattern", "(?i)(definition|response|request|schema|object|model|type|data|payload|dto)[0-9]*"));
    }

    @Test void schemaNamePascalCase() {
        assertParity("schema-name", "schema", Map.of("pattern", "(?i).*(request|response)", "case", "pascal-case"), true);
    }

    @Test void headerSchema() { assertParity("header-schema", "response", Map.of(), true); }

    @Test void requestBodyForbiddenOnDelete() {
        assertParity("request-body", "operation", Map.of("check", "forbidden-on-methods", "methods", "DELETE"), true);
    }

    @Test void requestBodyRequiredFlag() {
        assertParity("request-body", "operation", Map.of("check", "required-flag-missing"), true);
    }

    private static final String SENSITIVE_PATTERN = "password|secret|token|api[_-]?key|ssn|credit[_-]?card";

    @Test void sensitiveDataProperty() { assertParity("sensitive-data", "property", Map.of("pattern", SENSITIVE_PATTERN), true); }
    @Test void sensitiveDataQuery() { assertParity("sensitive-data", "query-parameter", Map.of("pattern", SENSITIVE_PATTERN), true); }
    @Test void sensitiveDataPath() { assertParity("sensitive-data", "path-parameter", Map.of("pattern", SENSITIVE_PATTERN), false); }
    @Test void sensitiveDataHeader() { assertParity("sensitive-data", "header", Map.of("pattern", SENSITIVE_PATTERN), true); }

    @Test void openapiVersionUnsupported() {
        assertParity("openapi-version", "api", Map.of("allowed", "2.0"), true);
    }

    @Test void openapiVersionSupported() {
        assertParity("openapi-version", "api", Map.of("allowed", "3.0,3.1"), false);
    }

    @Test void documentationCompletenessProperty() {
        assertParity("documentation-completeness", "property", Map.of("require", "both"), true);
    }

    @Test void documentationCompletenessParameter() {
        assertParity("documentation-completeness", "parameter", Map.of("require", "both"), true);
    }

    @Test void mediaTypeResponseAbsent() {
        assertParity("media-type", "media-type", Map.of("location", "response", "match", "absent"), true);
    }

    @Test void mediaTypeResponseNotAllowed() {
        assertParity("media-type", "media-type",
                Map.of("location", "response", "match", "not-allowed", "allowed", "application/json"), true);
    }

    @Test void mediaTypeResponseWildcard() {
        assertParity("media-type", "media-type", Map.of("location", "response", "match", "wildcard"), true);
    }

    @Test void mediaTypeRequestAbsent() {
        assertParity("media-type", "media-type", Map.of("location", "request", "match", "absent"), true);
    }

    // --- harness ---------------------------------------------------------

    private void assertParity(String detectorId, String scope, Map<String, Object> parameters) {
        assertParity(detectorId, scope, parameters, false);
    }

    private void assertParity(String detectorId, String scope, Map<String, Object> parameters, boolean expectFindings) {
        Map<String, Object> api = OpenApiMapAdapter.toMap(
                new OpenAPIV3Parser().readContents(CATALOGUE_SPEC, null, new ParseOptions()).getOpenAPI());

        Detector starDetector = new Detector(detectorId, "starlark",
                read("api-policy/detectors/" + detectorId + "/Detector.star"), List.of(scope), Map.of());
        Rule rule = new Rule("PARITY", "Parity", "Parity", detectorId, scope, parameters, "");

        List<Occurrence> starOccurrences = new StarlarkDetectorRuntime().execute(starDetector, api, rule);
        List<Occurrence> siftOccurrences = new SiftRuntime().execute(
                read("api-policy/detectors/" + detectorId + "/Detector.sift"), api, rule.asMap());

        assertEquals(starOccurrences, siftOccurrences, detectorId + " sift/star output differs");
        if (expectFindings) {
            assertFalse(starOccurrences.isEmpty(), detectorId + ": test spec should exercise the detector");
        }
    }

    private static String read(String path) {
        try (InputStream stream = SiftParityTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("missing resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
