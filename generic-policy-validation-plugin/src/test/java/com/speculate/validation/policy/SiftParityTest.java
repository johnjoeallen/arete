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
              /reports:
                get:
                  parameters:
                    - { name: search, in: query, schema: { type: string } }
                    - { name: fields, in: query, schema: { type: object } }
                  responses:
                    '200': { description: OK }
              /bulk-create/orders/{batchId}:
                post:
                  summary: Bulk create orders
                  responses: { '200': { description: OK } }
              /catalog/searchByName:
                put:
                  summary: Search catalog by criteria
                  responses: { '200': { description: OK } }
              /things/{thingKey}:
                get:
                  parameters:
                    - name: thingId
                      in: path
                      schema: { type: string }
                    - { name: verbose, in: query }
                  responses:
                    '200':
                      description: OK
                      content:
                        application/json:
                          schema: { type: object, properties: { a: { type: string } } }
                    '500': { description: boom }
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
                Composed:
                  allOf:
                    - $ref: '#/components/schemas/order_response'
                    - type: object
                      properties: { extra: { type: string } }
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

    @Test void statusClassServerError() {
        assertParity("status-class", "response", Map.of("forbidden", "server-error"), true);
    }

    @Test void schemaCompositionInline() {
        assertParity("schema-composition", "schema", Map.of("check", "inline-composition"), true);
    }

    @Test void schemaCompositionInlineBody() {
        assertParity("schema-composition", "operation", Map.of("check", "inline-body"), true);
    }

    @Test void parameterMaxCount() {
        assertParity("parameter", "operation", Map.of("check", "max-count", "maximum", 2), true);
    }

    @Test void parameterPathRequired() {
        assertParity("parameter", "parameter", Map.of("check", "path-required"), true);
    }

    @Test void parameterSchemaPresent() {
        assertParity("parameter", "parameter", Map.of("check", "schema-present"), true);
    }

    @Test void parameterTemplateMatch() {
        assertParity("parameter", "parameter", Map.of("check", "template-match"), true);
    }

    @Test void mediaTypeRequestAbsent() {
        assertParity("media-type", "media-type", Map.of("location", "request", "match", "absent"), true);
    }

    @Test void operationSummaryAbsent() {
        assertParity("operation", "operation", Map.of("summary", "absent"), true);
    }

    @Test void identifierNotString() {
        assertParity("identifier", "property",
                Map.of("name-pattern", "(^|[-_])(id|identifier|uuid)([-_]|$)", "check", "string"), true);
    }

    @Test void identifierMissingFormat() {
        assertParity("identifier", "property",
                Map.of("name-pattern", "(^|[-_])(id|identifier|uuid)([-_]|$)", "check", "format", "format", "uuid"), true);
    }

    @Test void bulkCreate() {
        assertParity("bulk-operation", "operation",
                Map.of("operation-type", "create", "expected-method", "POST", "payload", "collection"), true);
    }

    @Test void bulkSearchCriteria() {
        assertParity("bulk-operation", "operation",
                Map.of("method", "PUT", "target-selection", "search-criteria"), true);
    }

    @Test void sensitiveSearchQueryParam() {
        assertParity("sensitive-search", "query-parameter",
                Map.of("search-pattern", "(^|[-_])(q|query|search|term|text)([-_]|$)",
                        "sensitive-pattern", SENSITIVE_PATTERN), true);
    }

    @Test void sensitiveSearchOperation() {
        assertParity("sensitive-search", "operation",
                Map.of("search-pattern", "(^|[-_])(q|query|search|term|text)([-_]|$)",
                        "sensitive-pattern", SENSITIVE_PATTERN), false);
    }

    @Test void collectionCapabilityMissing() {
        assertParity("collection-capability", "operation",
                Map.of("name-pattern", "(^|[-_])(sort|order)([-_]|$)", "check", "present"), true);
    }

    @Test void collectionCapabilityRepresentation() {
        assertParity("collection-capability", "query-parameter",
                Map.of("name-pattern", "(^|[-_])(filter)([-_]|$)", "check", "string"), false);
    }

    @Test void paginationOperationMissing() {
        assertParity("pagination", "operation",
                Map.of("name-pattern", "(^|[-_])(page|offset|cursor)([-_]|$)", "check", "present"), true);
    }

    @Test void paginationParamInteger() {
        assertParity("pagination", "query-parameter",
                Map.of("name-pattern", "(^|[-_])(search)([-_]|$)", "check", "string"), false);
    }

    @Test void paginationLinkHeader() {
        assertParity("pagination", "response",
                Map.of("name-pattern", "(^|[-_])(page)([-_]|$)"), true);
    }

    @Test void resourcePathOperationVerb() {
        assertParity("resource-path", "path", Map.of("match", "operation-verb"), false);
    }

    @Test void resourcePathRpcStyle() {
        assertParity("resource-path", "operation", Map.of("match", "rpc-style"), false);
    }

    @Test void resourcePathTrailingSlash() {
        assertParity("resource-path", "path", Map.of("match", "trailing-slash"), false);
    }

    @Test void resourcePathEmbeddedId() {
        assertParity("resource-path", "path", Map.of("match", "embedded-identifier"), false);
    }

    @Test void resourcePathCustomAction() {
        assertParity("resource-path", "path", Map.of("match", "custom-action"), false);
    }

    @Test void errorResponseRequiredClass() {
        assertParity("error-response", "operation", Map.of("required-class", "success"), false);
    }

    @Test void errorResponseDescription() {
        assertParity("error-response", "response", Map.of("require-description", true), false);
    }

    @Test void errorResponseHeader() {
        assertParity("error-response", "response",
                Map.of("status", 500, "required-header", "Retry-After"), true);
    }

    @Test void authErrorOperation() {
        assertParity("authentication-error", "operation", Map.of("required-status", 401), false);
    }

    @Test void authErrorResponseHeader() {
        assertParity("authentication-error", "response",
                Map.of("required-status", 500, "required-header", "WWW-Authenticate"), true);
    }

    private static final String LINT_SPEC = """
            openapi: 3.0.0
            info: { title: T, version: 1.0.0 }
            paths:
              /x:
                get:
                  responses:
                    200: { description: ok }
                    '404':
                      description: nope
                      content: { application/json: { schema: { $ref: '#/components/schemas/Ghost' } } }
            """;

    @Test void documentLintParserMessage() {
        assertParity("document-lint", "api",
                Map.of("check", "parser-message", "pattern", "(?i)(#/\\S+ is missing|is not of type)"), true, LINT_SPEC);
    }

    @Test void documentLintNumericStatusKey() {
        assertParity("document-lint", "api", Map.of("check", "numeric-status-key"), true, LINT_SPEC);
    }

    @Test void hostnameConvention() {
        assertParity("hostname", "api", Map.of("convention", "lowercase-hyphenated"), true);
    }

    @Test void serverUrlInternalHost() {
        assertParity("server-url", "api", Map.of("check", "internal-host"), false);
    }

    @Test void enumValuesNoDuplicates() {
        assertParity("enum-values", "property", Map.of("check", "no-duplicates"), true, """
                openapi: 3.0.0
                info: { title: T, version: 1.0.0 }
                paths: {}
                components:
                  schemas:
                    S:
                      type: object
                      properties:
                        status: { type: string, enum: [A, B, A] }
                        clean: { type: string, enum: [X, Y] }
                """);
    }

    @Test void pathSetUnique() {
        assertParity("path-set", "api", Map.of("check", "unique"), true, """
                openapi: 3.0.0
                info: { title: T, version: 1.0.0 }
                paths:
                  /pets/{id}: { get: { responses: { '200': { description: ok } } } }
                  /pets/{petId}: { get: { responses: { '200': { description: ok } } } }
                  /owners: { get: { responses: { '200': { description: ok } } } }
                """);
    }




    @Test void serverUrlPattern() {
        assertParity("server-url", "api",
                Map.of("check", "url-pattern", "pattern", "https://(api|sandbox)\\.example\\.com/.*"), false);
    }

    // --- harness ---------------------------------------------------------

    private void assertParity(String detectorId, String scope, Map<String, Object> parameters) {
        assertParity(detectorId, scope, parameters, false);
    }

    private void assertParity(String detectorId, String scope, Map<String, Object> parameters, boolean expectFindings) {
        assertParity(detectorId, scope, parameters, expectFindings, CATALOGUE_SPEC);
    }

    private void assertParity(String detectorId, String scope, Map<String, Object> parameters, boolean expectFindings, String spec) {
        var parsed = new OpenAPIV3Parser().readContents(spec, null, new ParseOptions());
        Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), spec);

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
