package net.dublinux.arete.validation.policy;

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
 * Every bundled rule that ships both Matcher.dsl and Matcher.groovy must produce exactly
 * the same diagnostics from both implementations. for a representative spec.
 */
class DistillGroovyParityTest {

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

    @Test void namingPropertyCamelCase() {
        assertParity("naming", "property", Map.of("convention", "camelCase", "match", "non-conforming"), false);
    }

    @Test void namingPathSegmentKebab() {
        assertParity("naming", "path-segment", Map.of("convention", "kebab-case", "match", "non-conforming"), false);
    }

    @Test void namingUnsupportedCharacter() {
        assertParity("naming", "property", Map.of("match", "unsupported-character"), false);
    }

    @Test void namingArraySingular() {
        assertParity("naming", "property", Map.of("schema-type", "array", "semantic", "singular"), false);
    }

    @Test void namingCollection() {
        assertParity("naming", "path-segment", Map.of("semantic", "collection"), true);
    }

    @Test void namingSchemaSuffixRequest() {
        assertParity("naming", "schema", Map.of("suffix", "Request", "match", "present"), true);
    }

    @Test void namingSchemaSuffixResponse() {
        assertParity("naming", "schema", Map.of("suffix", "Response", "match", "present"), false);
    }

    @Test void namingPathParamSnake() {
        assertParity("naming", "path-parameter", Map.of("convention", "snake_case", "match", "non-conforming"), false);
    }

    @Test void namingQueryParamSnake() {
        assertParity("naming", "query-parameter", Map.of("convention", "snake_case", "match", "non-conforming"), false);
    }

    @Test void namingHeaderHyphenated() {
        assertParity("naming", "header", Map.of("convention", "hyphenated", "match", "non-conforming"), false);
    }

    @Test void textStyleInitialCapital() {
        assertParity("text-style", "operation-summary", Map.of("initial-capital", false), false);
    }

    @Test void textStyleSentenceCase() {
        assertParity("text-style", "operation-summary", Map.of("convention", "sentence-case"), false);
    }

    @Test void textStyleTrailingPeriod() {
        assertParity("text-style", "operation-summary", Map.of("trailing-period", "present"), false);
    }

    @Test void textStyleMaximumLength() {
        assertParity("text-style", "operation-summary", Map.of("maximum-length", 120), false);
    }

    @Test void textStyleNonActionOriented() {
        assertParity("text-style", "operation-summary", Map.of("match", "non-action-oriented"), true);
    }

    @Test void apiTitleHouseStyle() {
        assertParity("api-title", "api",
                Map.of("suffix", "API", "forbidden", "PoC,Test,WIP,Draft", "case", "title-case"), false);
    }

    @Test void extensionsAllowed() {
        assertParity("extensions", "api", Map.of("allowed", "x-api-id,x-audience,x-extensible-enum"), false);
    }

    @Test void queryCollectionStyle() {
        assertParity("query-collection", "query-parameter", Map.of("style", "form", "explode", true), false);
    }

    @Test void versioningUri() {
        assertParity("versioning", "path", Map.of("location", "uri", "match", "present"), false);
    }

    @Test void versioningHeader() {
        assertParity("versioning", "header", Map.of("location", "header", "match", "present"), false);
    }

    @Test void versioningMediaType() {
        assertParity("versioning", "media-type", Map.of("location", "media-type", "match", "present"), false);
    }

    @Test void versioningAbsent() {
        assertParity("versioning", "api", Map.of("match", "absent"), false);
    }

    @Test void responseHeaderOptional() {
        assertParity("response-header", "response", Map.of("status", 200, "header", "Link", "required", false), false);
    }

    @Test void responseHeaderRequiredList() {
        assertParity("response-header", "response",
                Map.of("status", 429, "headers", "RateLimit-Limit,RateLimit-Remaining,RateLimit-Reset", "required", true), false);
    }

    @Test void responseHeaderCreatedLocation() {
        assertParity("response-header", "response", Map.of("status", 201, "header", "Location", "required", true), false);
    }

    @Test void responseCodeJsonObject() {
        assertParity("response-code", "response", Map.of("response-shape", "json-object"), false);
    }

    @Test void responseCodeProblemJson() {
        assertParity("response-code", "response", Map.of("error-format", "problem-json"), true);
    }

    @Test void responseCodeCreateStatus() {
        assertParity("response-code", "operation", Map.of("operation-type", "create", "required-status", 201), true);
    }

    @Test void responseCodeRetrievalStatus() {
        assertParity("response-code", "operation",
                Map.of("operation-type", "identifiable-resource-retrieval", "required-status", 404), true);
    }

    @Test void responseCodeSemanticConflict() {
        assertParity("response-code", "response", Map.of("match", "semantic-conflict"), false);
    }

    private static final String HOUSE_STYLE_SPEC = """
            openapi: 3.0.0
            info: { title: draft payments poc, version: v2 }
            x-weird: true
            servers: [ { url: https://internal.corp/v2 } ]
            paths:
              /v2/Payments:
                get:
                  summary: get all the payments that exist
                  parameters:
                    - name: tags
                      in: query
                      style: spaceDelimited
                      schema: { type: array, items: { type: string } }
                  responses:
                    '200':
                      description: error occurred
                      content: { application/json: { schema: { type: string } } }
                    '429': { description: too many }
                    '500': { description: boom }
            """;

    @Test void apiTitleHouseStyleDiagnostics() {
        assertParity("api-title", "api",
                Map.of("suffix", "API", "forbidden", "PoC,Test,WIP,Draft", "case", "title-case"), true, HOUSE_STYLE_SPEC);
    }

    @Test void extensionsUnknownExtension() {
        assertParity("extensions", "api", Map.of("allowed", "x-api-id"), true, HOUSE_STYLE_SPEC);
    }

    @Test void queryCollectionWrongStyle() {
        assertParity("query-collection", "query-parameter", Map.of("style", "form", "explode", true), true, HOUSE_STYLE_SPEC);
    }

    @Test void versioningUriPresent() {
        assertParity("versioning", "path", Map.of("location", "uri", "match", "present"), true, HOUSE_STYLE_SPEC);
    }

    @Test void responseHeaderMissingList() {
        assertParity("response-header", "response",
                Map.of("status", 429, "headers", "RateLimit-Limit,RateLimit-Remaining,RateLimit-Reset", "required", true),
                true, HOUSE_STYLE_SPEC);
    }

    @Test void responseCodeJsonObjectDiagnostic() {
        assertParity("response-code", "response", Map.of("response-shape", "json-object"), true, HOUSE_STYLE_SPEC);
    }

    @Test void textStyleTrailingPeriodMissing() {
        assertParity("text-style", "operation-summary", Map.of("trailing-period", "present"), false, HOUSE_STYLE_SPEC);
    }

    private static final String SCHEMA_SPEC = """
            openapi: 3.0.0
            info:
              title: Widget API
              description: Widgets.
              contact: { name: Team, email: team@example.com }
              version: 1.2
            components:
              securitySchemes:
                bearerAuth: { type: http, scheme: bearer }
              schemas:
                Widget:
                  type: object
                  required: [id, name]
                  properties:
                    id: { type: integer }
                    name: { type: string }
                    price: { type: number }
                    count: { type: integer, format: int32 }
                    tags: { type: array, items: { type: string } }
                    status: { type: string, enum: [ACTIVE, inactive, 3] }
                    kind:
                      type: string
                      enum: [A, B]
                      x-extensible-enum: [A, B]
                    code: { type: string, pattern: '^[A-Z]{3}$', minLength: 3, maxLength: 3, example: 'ab' }
                    ratio: { type: number, minimum: 1, maximum: 10, example: 42 }
                  example:
                    id: 1
                WidgetList:
                  type: object
                  properties:
                    items: { type: array, items: { $ref: '#/components/schemas/Widget' } }
            paths:
              /widgets:
                get:
                  security: []
                  responses: { '200': { description: ok } }
                post:
                  responses:
                    '201':
                      description: created
                      headers:
                        X-Widget-Token: { description: token }
            security:
              - bearerAuth: []
            """;

    @Test void schemaNumberFormatAbsent() {
        assertParity("schema", "property", Map.of("type", "number", "format", "absent"), true, SCHEMA_SPEC);
    }

    @Test void schemaArrayMaxItemsAbsent() {
        assertParity("schema", "property", Map.of("type", "array", "max-items", "absent"), true, SCHEMA_SPEC);
    }

    @Test void schemaEnumUpperSnake() {
        assertParity("schema", "property", Map.of("enum-case", "upper-snake-case"), true, SCHEMA_SPEC);
    }

    @Test void schemaEnumTypeConsistent() {
        assertParity("schema", "property", Map.of("enum-type", "consistent"), false, SCHEMA_SPEC);
    }

    @Test void schemaExtensibleRequired() {
        assertParity("schema", "property", Map.of("extensible", "required"), true, SCHEMA_SPEC);
    }

    @Test void schemaStringEnumPresent() {
        assertParity("schema", "property", Map.of("type", "string", "enum", "present"), true, SCHEMA_SPEC);
    }

    @Test void schemaFormatAbsent() {
        assertParity("schema", "property", Map.of("format", "absent"), true, SCHEMA_SPEC);
    }

    @Test void schemaOptionalNullable() {
        assertParity("schema", "property", Map.of("required", false, "nullable", true, "semantics", "undefined"), false, SCHEMA_SPEC);
    }

    @Test void exampleCoversRequired() {
        assertParity("example-validity", "schema", Map.of("check", "covers-required"), true, SCHEMA_SPEC);
    }

    @Test void exampleSatisfiesConstraints() {
        assertParity("example-validity", "property", Map.of("check", "satisfies-constraints"), true, SCHEMA_SPEC);
    }

    @Test void securitySchemeMissing() {
        assertParity("security", "operation", Map.of("scheme", "bearerAuth"), true, SCHEMA_SPEC);
    }

    @Test void securitySchemeWithScopes() {
        assertParity("security", "operation", Map.of("scheme", "bearerAuth", "scopes", "read"), true, SCHEMA_SPEC);
    }

    @Test void metadataComplete() {
        assertParity("metadata", "api", Map.of("required", "complete"), true, SCHEMA_SPEC);
    }

    @Test void metadataIdentifier() {
        assertParity("metadata", "api", Map.of("required", "identifier"), true, SCHEMA_SPEC);
    }

    @Test void metadataAudience() {
        assertParity("metadata", "api", Map.of("required", "audience"), true, SCHEMA_SPEC);
    }

    @Test void proprietaryHeaderNotAllowed() {
        assertParity("proprietary-header", "header", Map.of("allowed", "X-Request-Id,X-Correlation-Id"), true, SCHEMA_SPEC);
    }

    private static final String OPS_SPEC = """
            openapi: 3.0.0
            info: { title: T, version: 1.0.0 }
            paths:
              /orders:
                get:
                  operationId: listOrders
                  tags: [orders]
                  responses: { '200': { description: ok } }
                post:
                  operationId: listOrders
                  tags: [orders]
                  responses: { '200': { description: ok } }
              /orders/{id}/items/{itemId}/tags:
                get:
                  responses:
                    '200': { description: ok }
                    '400':
                      description: bad
                      content: { application/json: { example: { error: nope } } }
                    '422':
                      description: bad too
                      content: { application/json: { example: { error: nope } } }
              /customers:
                get: { responses: { '200': { description: ok } } }
              /invoices:
                get: { responses: { '200': { description: ok } } }
            """;

    @Test void operationMetadataUniqueOperationId() {
        assertParity("operation-metadata", "operation", Map.of("check", "unique-operation-id"), true, OPS_SPEC);
    }

    @Test void operationMetadataTagsPresent() {
        assertParity("operation-metadata", "operation", Map.of("check", "tags-present"), true, OPS_SPEC);
    }

    @Test void responseExampleUniqueErrorPayloads() {
        assertParity("response-example", "operation", Map.of("check", "unique-error-payloads"), true, OPS_SPEC);
    }

    @Test void pathCountMaximumResources() {
        assertParity("path-count", "api", Map.of("maximum", 2), true, OPS_SPEC);
    }

    @Test void pathCountMaximumDepth() {
        assertParity("path-count", "api", Map.of("maximum", 8, "maximum-depth", 2), true, OPS_SPEC);
    }

    @Test void pathCountNestedRoot() {
        assertParity("path-count", "api", Map.of("maximum", 8, "nested-root", true), true, OPS_SPEC);
    }

    @Test void pathCountWithinLimits() {
        assertParity("path-count", "api", Map.of("maximum", 8), false, OPS_SPEC);
    }

    // --- harness ---------------------------------------------------------

    private void assertParity(String matcherId, String scope, Map<String, Object> parameters) {
        assertParity(matcherId, scope, parameters, false);
    }

    private void assertParity(String matcherId, String scope, Map<String, Object> parameters, boolean expectFindings) {
        assertParity(matcherId, scope, parameters, expectFindings, CATALOGUE_SPEC);
    }

    private void assertParity(String matcherId, String scope, Map<String, Object> parameters, boolean expectFindings, String spec) {
        var parsed = new OpenAPIV3Parser().readContents(spec, null, new ParseOptions());
        Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), spec);

        String groovySource = readOptional("api-policy/matchers/" + matcherId + "/Matcher.groovy");
        if (groovySource == null) return;
        Matcher groovyRule = new Matcher(matcherId, "groovy", groovySource, List.of(scope), Map.of());
        PolicyRule rule = new PolicyRule("PARITY", "Parity", "Parity", matcherId, scope, parameters, "");

        List<Diagnostic> groovyDiagnostics = new GroovyMatcherEvaluator().execute(groovyRule, api, rule);
        List<Diagnostic> distillDiagnostics = new DistillMatcherEvaluator().execute(
                read("api-policy/matchers/" + matcherId + "/Matcher.dsl"), api, rule.asMap());

        assertEquals(groovyDiagnostics, distillDiagnostics, matcherId + " distill/groovy output differs");
        if (expectFindings) {
            assertFalse(groovyDiagnostics.isEmpty(), matcherId + ": test spec should exercise the rule");
        }
    }

    private static String read(String path) {
        try (InputStream stream = DistillGroovyParityTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("missing resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readOptional(String path) {
        try (InputStream stream = DistillGroovyParityTest.class.getClassLoader().getResourceAsStream(path)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
