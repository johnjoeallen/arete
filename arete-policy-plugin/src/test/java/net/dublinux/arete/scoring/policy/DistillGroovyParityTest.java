package net.dublinux.arete.scoring.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    @Test void mediaTypeRequestWildcard() {
        assertParity("media-type", "media-type", Map.of("location", "request", "match", "wildcard"), false);
    }

    @Test void mediaTypeRequestNotAllowed() {
        // No fixture spec documents a request body with a disallowed media type; this
        // pins that the request/not-allowed stanza stays parity-correct (both empty).
        assertParity("media-type", "media-type",
                Map.of("location", "request", "match", "not-allowed", "allowed", "application/json"), false);
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

    @Test void textStyleMinimumWords() {
        assertParity("text-style", "operation-summary", Map.of("minimum-words", 3), false);
    }

    @Test void textStyleMaximumWordLength() {
        assertParity("text-style", "operation-summary", Map.of("maximum-word-length", 12), false);
    }

    private static final List<String> ACTION_VERBS = List.of(
            "Get", "List", "Create", "Update", "Delete", "Replace", "Search", "Find", "Cancel", "Activate", "Deactivate");

    @Test void textStyleNonActionOriented() {
        // The loader delivers action-prefixes as a list; test with that form.
        assertParity("text-style", "operation-summary",
                Map.of("match", "non-action-oriented", "action-prefixes", ACTION_VERBS), true);
    }

    /** action-prefixes drives summary.trim().startsWith(list). */
    @Test void textStyleNonActionOrientedCustomPrefixes() {
        assertParity("text-style", "operation-summary",
                Map.of("match", "non-action-oriented", "action-prefixes", List.of("Fetch", "Remove")), true);
        assertParity("text-style", "operation-summary",
                Map.of("match", "non-action-oriented", "action-prefixes", ACTION_VERBS), true, HOUSE_STYLE_SPEC);
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

    @Test void schemaBoundsComplete() {
        assertParity("schema", "property", Map.of("bounds", "complete"), true, SCHEMA_SPEC);
    }

    @Test void schemaMaxLengthAbsent() {
        assertParity("schema", "property", Map.of("max-length", "absent"), true, SCHEMA_SPEC);
    }

    @Test void schemaIntegerEnumPresent() {
        assertParity("schema", "property", Map.of("type", "integer", "enum", "present"), false, SCHEMA_SPEC);
    }

    /** type-only (or empty) config selects a property set but no check — nothing is reported. */
    @Test void schemaWithNoCheckParameterReportsNothing() {
        assertParity("schema", "property", Map.of("type", "string"), false, SCHEMA_SPEC);
        assertParity("schema", "property", Map.of(), false, SCHEMA_SPEC);
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

    /**
     * Runs every dual-implemented matcher through both engines against all five
     * fixture specs and every scope it declares, supplying a loader-valid value
     * for each required parameter, and reports the aggregate cost of each engine
     * plus where their diagnostics agree.
     *
     * <p>Complements the curated {@code assertParity} cases above (which pin
     * specific realistic parameter sets): this is the broad structural sweep.
     * Groovy and Distill must produce identical diagnostics for every
     * combination. The timing report needs {@code -Darete.benchmark=true}.
     */
    @Test
    void fullSweepParityAndPerformance() {
        boolean report = Boolean.getBoolean("arete.benchmark");
        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("catalogue", CATALOGUE_SPEC);
        specs.put("lint", LINT_SPEC);
        specs.put("house-style", HOUSE_STYLE_SPEC);
        specs.put("schema", SCHEMA_SPEC);
        specs.put("ops", OPS_SPEC);

        Map<String, Map<String, Object>> apis = new LinkedHashMap<>();
        for (Map.Entry<String, String> s : specs.entrySet()) {
            var parsed = new OpenAPIV3Parser().readContents(s.getValue(), null, new ParseOptions());
            apis.put(s.getKey(), OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), s.getValue()));
        }

        PolicyBundle bundle = new PolicyBundleLoader()
                .load(new ClasspathBundleResources(getClass().getClassLoader()));

        GroovyMatcherEvaluator groovy = new GroovyMatcherEvaluator();
        DistillMatcherEvaluator distill = new DistillMatcherEvaluator();

        int combos = 0, mismatches = 0, exercised = 0, agreed = 0;
        long groovyNanos = 0, distillNanos = 0;
        List<String> rows = new ArrayList<>();

        for (Matcher descriptor : bundle.matchers().values()) {
            String groovySource = readOptional("api-policy/matchers/" + descriptor.id() + "/Matcher.groovy");
            if (groovySource == null) continue;
            String dslSource = read("api-policy/matchers/" + descriptor.id() + "/Matcher.dsl");
            Matcher groovyMatcher = new Matcher(descriptor.id(), "groovy", groovySource, descriptor.scopes(), Map.of());
            Matcher dslMatcher = new Matcher(descriptor.id(), "distill", dslSource, descriptor.scopes(), Map.of());
            @SuppressWarnings("unchecked")
            groovy.lang.Closure<Object> groovyClosure =
                    (groovy.lang.Closure<Object>) new groovy.lang.GroovyShell().evaluate(groovySource);

            // Supply every required parameter with a loader-valid value, so the
            // sweep exercises each matcher in a configuration it could actually
            // be deployed in (the bundle loader rejects a rule that omits one).
            Map<String, Object> parameters = requiredParameters(descriptor);

            long scopeGroovy = 0, scopeDistill = 0, mg = 0, md = 0;
            int mCombos = 0, mFindings = 0;
            for (String scope : descriptor.scopes()) {
                for (Map.Entry<String, Map<String, Object>> a : apis.entrySet()) {
                    PolicyRule rule = new PolicyRule("SWEEP", "Sweep", "Sweep", descriptor.id(), scope, parameters, "");
                    combos++; mCombos++;
                    // Both engines must reach the same verdict (findings, or failure).
                    Object g0 = outcome(() -> groovy.execute(groovyMatcher, a.getValue(), rule));
                    Object d0 = outcome(() -> distill.execute(dslMatcher, a.getValue(), rule));
                    if (!java.util.Objects.equals(g0, d0)) {
                        mismatches++;
                        rows.add(String.format("DIVERGES %-26s scope=%-18s spec=%-11s groovy=%s distill=%s",
                                descriptor.id(), scope, a.getKey(), summarise(g0), summarise(d0)));
                        continue;
                    }
                    agreed++;
                    if (g0 instanceof List<?> l && !l.isEmpty()) { exercised++; mFindings++; }

                    if (!report) continue; // timing only when explicitly benchmarking

                    Map<String, Object> ruleMap = rule.asMap();
                    for (int i = 0; i < 50; i++) { groovyClosure.call(a.getValue(), ruleMap); distill.execute(dslMatcher, a.getValue(), rule); }
                    long best = Long.MAX_VALUE;
                    for (int rep = 0; rep < 5; rep++) {
                        long t0 = System.nanoTime();
                        for (int i = 0; i < 100; i++) groovyClosure.call(a.getValue(), ruleMap);   // compiled once, reused
                        long t1 = System.nanoTime();
                        for (int i = 0; i < 100; i++) distill.execute(dslMatcher, a.getValue(), rule);
                        long t2 = System.nanoTime();
                        if ((t1 - t0) < best) { best = t1 - t0; mg = (t1 - t0) / 100; md = (t2 - t1) / 100; }
                    }
                    scopeGroovy += mg; scopeDistill += md;
                }
            }
            long perCallGroovy = scopeGroovy / mCombos, perCallDistill = scopeDistill / mCombos;
            groovyNanos += perCallGroovy; distillNanos += perCallDistill;
            rows.add(String.format("| `%s` | %d | %d | %.1f | %.1f | %.1f× |",
                    descriptor.id(), mCombos, mFindings,
                    perCallGroovy / 1000.0, perCallDistill / 1000.0,
                    (double) perCallGroovy / Math.max(1, perCallDistill)));
        }

        rows.stream().filter(r -> r.startsWith("DIVERGES")).forEach(System.out::println);
        assertEquals(0, mismatches, "groovy and distill produced different diagnostics for "
                + mismatches + " matcher/scope/spec combination(s) (see DIVERGES lines above)");

        // The deployed engine must stay clearly ahead of compiled Groovy
        // (generous margin for CI timing noise). Only measured under -Darete.benchmark.
        if (report) {
            assertFalse(distillNanos * 2 > groovyNanos,
                    "Distill (cached) lost its margin over Groovy (compiled): distill="
                            + distillNanos / 1000.0 + "us groovy=" + groovyNanos / 1000.0 + "us");
        }

        System.out.println("\n=== full groovy/distill sweep (" + combos + " matcher x scope x spec combos) ===");
        if (report) {
            System.out.println("| Matcher | Combos | Findings | Groovy µs/call | Distill µs/call | Speedup |");
            System.out.println("|---|--:|--:|--:|--:|--:|");
            rows.stream().filter(r -> r.startsWith("|")).sorted().forEach(System.out::println);
        }
        System.out.printf("%n%-34s %d%n", "combos compared", combos);
        System.out.printf("%-34s %d%n", "identical diagnostics", agreed);
        System.out.printf("%-34s %d%n", "combos with findings", exercised);
        System.out.printf("%-34s %.1f µs%n", "groovy per-call, summed over rules", groovyNanos / 1000.0);
        System.out.printf("%-34s %.1f µs%n", "distill per-call, summed over rules", distillNanos / 1000.0);
        System.out.printf("%-34s %.1fx faster (mean)%n", "distill vs groovy", (double) groovyNanos / distillNanos);
    }

    /**
     * Adds a hand-written Java baseline to the engine comparison for a
     * representative slice of matchers ({@link JavaMatchers}). For each, runs
     * Groovy (compiled once), Distill (cached parse) and plain Java against all
     * five fixture specs with a realistic parameter set, asserts all three
     * agree, and reports µs/call. Timing needs {@code -Darete.benchmark=true}.
     */
    @Test
    void javaBaselineComparison() {
        boolean report = Boolean.getBoolean("arete.benchmark");

        Map<String, String> specs = new LinkedHashMap<>();
        specs.put("catalogue", CATALOGUE_SPEC);
        specs.put("lint", LINT_SPEC);
        specs.put("house-style", HOUSE_STYLE_SPEC);
        specs.put("schema", SCHEMA_SPEC);
        specs.put("ops", OPS_SPEC);
        Map<String, Map<String, Object>> apis = new LinkedHashMap<>();
        specs.forEach((name, text) -> {
            var parsed = new OpenAPIV3Parser().readContents(text, null, new ParseOptions());
            apis.put(name, OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), text));
        });

        String semanticsSpec = """
                openapi: 3.0.0
                info: { title: T, version: 1.0.0 }
                paths:
                  /orders/{id}:
                    get: { summary: Delete the order, responses: { '200': { description: ok } } }
                    post: { summary: Replace the order, responses: { '200': { description: ok } } }
                  /widgets:
                    get: { summary: List widgets, responses: { '200': { description: ok } } }
                """;
        String collisionSpec = """
                openapi: 3.0.0
                info: { title: T, version: 1.0.0 }
                paths:
                  /pets/{id}: { get: { responses: { '200': { description: ok } } } }
                  /pets/{petId}: { get: { responses: { '200': { description: ok } } } }
                  /owners: { get: { responses: { '200': { description: ok } } } }
                """;

        record Case(String matcher, String scope, Map<String, Object> parameters, String spec) { }
        List<Case> cases = List.of(
                new Case("hostname", "api", Map.of("convention", "lowercase-hyphenated"), null),
                new Case("date-time-name", "property", Map.of("suffix", "_at"), null),
                new Case("status-class", "response", Map.of("forbidden", "server-error"), null),
                new Case("operation-semantics", "operation",
                        Map.of("match", "inconsistent-method-resource-semantics"), semanticsSpec),
                new Case("path-set", "path", Map.of("check", "unique"), collisionSpec));

        GroovyMatcherEvaluator groovy = new GroovyMatcherEvaluator();
        DistillMatcherEvaluator distill = new DistillMatcherEvaluator();

        System.out.println("\n=== Java baseline vs Distill vs Groovy ===");
        System.out.println("| Matcher | Findings | Groovy µs | Distill µs | Java µs | Distill/Java | Groovy/Java |");
        System.out.println("|---|--:|--:|--:|--:|--:|--:|");

        for (Case c : cases) {
            String groovySource = read("api-policy/matchers/" + c.matcher() + "/Matcher.groovy");
            String dslSource = read("api-policy/matchers/" + c.matcher() + "/Matcher.dsl");
            Matcher groovyMatcher = new Matcher(c.matcher(), "groovy", groovySource, List.of(c.scope()), Map.of());
            Matcher dslMatcher = new Matcher(c.matcher(), "distill", dslSource, List.of(c.scope()), Map.of());
            @SuppressWarnings("unchecked")
            groovy.lang.Closure<Object> groovyClosure =
                    (groovy.lang.Closure<Object>) new groovy.lang.GroovyShell().evaluate(groovySource);
            JavaMatchers.Matcher javaMatcher = JavaMatchers.BY_ID.get(c.matcher());

            Map<String, Map<String, Object>> caseApis = apis;
            if (c.spec() != null) {
                var parsed = new OpenAPIV3Parser().readContents(c.spec(), null, new ParseOptions());
                caseApis = Map.of("case", OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), c.spec()));
            }

            long gNanos = 0, dNanos = 0, jNanos = 0;
            int findings = 0;
            for (Map.Entry<String, Map<String, Object>> a : caseApis.entrySet()) {
                Map<String, Object> api = a.getValue();
                PolicyRule rule = new PolicyRule("BASE", "Base", "Base", c.matcher(), c.scope(), c.parameters(), "");
                Map<String, Object> ruleMap = rule.asMap();

                List<Diagnostic> g = groovy.execute(groovyMatcher, api, rule);
                List<Diagnostic> d = distill.execute(dslMatcher, api, rule);
                List<Diagnostic> j = javaMatcher.apply(api, ruleMap);
                assertEquals(g, d, c.matcher() + " / " + a.getKey() + ": groovy vs distill");
                assertEquals(g, j, c.matcher() + " / " + a.getKey() + ": groovy vs java");
                findings += d.size();

                if (!report) continue;
                for (int i = 0; i < 100; i++) { groovyClosure.call(api, ruleMap); distill.execute(dslMatcher, api, rule); javaMatcher.apply(api, ruleMap); }
                long bestG = Long.MAX_VALUE, bestD = Long.MAX_VALUE, bestJ = Long.MAX_VALUE;
                for (int rep = 0; rep < 5; rep++) {
                    long t0 = System.nanoTime();
                    for (int i = 0; i < 200; i++) groovyClosure.call(api, ruleMap);
                    long t1 = System.nanoTime();
                    for (int i = 0; i < 200; i++) distill.execute(dslMatcher, api, rule);
                    long t2 = System.nanoTime();
                    for (int i = 0; i < 200; i++) javaMatcher.apply(api, ruleMap);
                    long t3 = System.nanoTime();
                    bestG = Math.min(bestG, t1 - t0);
                    bestD = Math.min(bestD, t2 - t1);
                    bestJ = Math.min(bestJ, t3 - t2);
                }
                gNanos += bestG / 200; dNanos += bestD / 200; jNanos += bestJ / 200;
            }

            if (report) {
                int n = caseApis.size();
                double g = gNanos / 1000.0 / n, d = dNanos / 1000.0 / n, j = jNanos / 1000.0 / n;
                System.out.printf("| `%s` | %d | %.2f | %.2f | %.2f | %.1f× | %.1f× |%n",
                        c.matcher(), findings, g, d, j,
                        d / Math.max(0.001, j), g / Math.max(0.001, j));
            }
        }
    }

    /** A loader-valid value for every {@code required} parameter the matcher declares. */
    private static Map<String, Object> requiredParameters(Matcher descriptor) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        descriptor.parameters().forEach((name, definition) -> {
            if (!definition.required()) return;
            parameters.put(name, switch (definition.type()) {
                case "enum" -> definition.values().get(0);
                case "integer" -> 1;
                case "boolean" -> true;
                default -> name.contains("pattern") ? ".*" : "x"; // strings; a bare pattern must still compile
            });
        });
        return parameters;
    }

    private static String summarise(Object outcome) {
        if (!(outcome instanceof List<?> list)) return "ERROR";
        if (list.isEmpty()) return "[]";
        return list.size() + " finding(s)";
    }

    /** The diagnostics a matcher produced, or a marker string when it failed (e.g. missing required parameters). */
    private static Object outcome(java.util.concurrent.Callable<List<Diagnostic>> call) {
        try {
            return call.call();
        } catch (Exception e) {
            return "ERROR"; // both engines only reach here on missing parameters; the verdict, not the message, must match
        }
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
