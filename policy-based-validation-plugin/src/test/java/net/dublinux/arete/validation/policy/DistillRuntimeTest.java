package net.dublinux.arete.validation.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DistillMatcherEvaluatorTest {
    private final DistillMatcherEvaluator runtime = new DistillMatcherEvaluator();

    @Test
    void validationRejectsUnknownFunctionsBeforeExecution() {
        Matcher matcher = new Matcher("test", "distill",
                "distill(api, rule) { return blah(); }", List.of("api"), Map.of());

        assertThrows(BundleValidationException.class, () -> runtime.validate(matcher));
    }

    @Test
    void validationRejectsUnknownPropertiesBeforeExecution() {
        Matcher matcher = new Matcher("test", "distill",
                "distill(api, rule) { return api.notAProperty; }", List.of("api"), Map.of());

        assertThrows(BundleValidationException.class, () -> runtime.validate(matcher));
    }

    @Test
    void occurrenceIsTheSupportedFindingBuiltin() {
        assertEquals(List.of(new Diagnostic("/", "API", "Found")), runtime.execute(
                "distill(api, rule) { return [occurrence(\"/\", \"API\", \"Found\")]; }",
                Map.of(), Map.of("parameters", Map.of())));
    }

    @Test
    void safeNavigationHandlesMissingProperties() {
        assertEquals(List.of(), runtime.execute(
                "distill(api, rule) { return api.paths.expand { path -> path.operationDetails.filter { operation -> operation.summary?.trim() == \"x\" }.map { operation -> occurrence(operation.pointer, path.path, \"x\") } }; }",
                Map.of("paths", List.of(Map.of("operationDetails", List.of(Map.of())))),
                Map.of("parameters", Map.of())));
    }

    @Test
    void safeNavigationCanMatchMissingProperties() {
        assertEquals(1, runtime.execute(
                "distill(api, rule) { return api.paths.expand { path -> path.operationDetails.filter { operation -> !operation.summary?.trim() }.map { operation -> occurrence(operation.pointer, path.path, \"missing\") } }; }",
                Map.of("paths", List.of(Map.of("path", "/books", "operationDetails", List.of(Map.of())))),
                Map.of("parameters", Map.of())).size());
    }

    @Test
    void wordsSplitsProseIntoSubstantiveTokens() {
        // "Get  the widget — v2!" -> [Get, the, widget, v2] : whitespace runs collapse,
        // the em dash drops (no letter), trailing "!" is stripped. "-" alone has no word.
        assertEquals(1, runtime.execute(
                "distill(api, rule) { return api.values.filter { v -> size(words(v)) == 4 }.map { v -> occurrence(\"/\", \"v\", \"4\") }; }",
                Map.of("values", java.util.List.of("Get  the widget — v2!", "one two")),
                Map.of("parameters", Map.of())).size());
        // too few words
        assertEquals(1, runtime.execute(
                "distill(api, rule) { return api.values.filter { v -> size(words(v)) < 3 }.map { v -> occurrence(\"/\", \"v\", \"few\") }; }",
                Map.of("values", java.util.List.of("List", "List all customers")),
                Map.of("parameters", Map.of())).size());
        // per-word length
        assertEquals(1, runtime.execute(
                "distill(api, rule) { return api.values.filter { v -> words(v).any { w -> w.length > 10 } }.map { v -> occurrence(\"/\", \"v\", \"long\") }; }",
                Map.of("values", java.util.List.of("Returns TheFullyExpandedAggregate here", "Returns the aggregate")),
                Map.of("parameters", Map.of())).size());
    }

    @Test
    void isBlankMatchesNullEmptyAndWhitespaceOnlyStrings() {
        assertEquals(3, runtime.execute(
                "distill(api, rule) { return api.values.filter { value -> value is blank }.map { value -> occurrence(\"/\", \"value\", \"blank\") }; }",
                Map.of("values", java.util.Arrays.asList(null, "", "  ", "text")),
                Map.of("parameters", Map.of())).size());
    }

    @Test
    void canFindOperationWithoutRequestBodyUsingConfiguredMethod() {
        assertEquals(1, runtime.execute("""
                distill(api, rule) {
                    return api.paths
                        .expand { path ->
                            path.operationDetails
                                .filter { operation ->
                                    operation.method == rule.parameters["method"]
                                        && !operation.requestBodyPresent
                                }
                                .map { operation -> occurrence(operation.pointer, path.path, "missing") }
                        };
                }
                """, Map.of("paths", List.of(Map.of("path", "/books", "operationDetails", List.of(
                        Map.of("method", "POST", "requestBodyPresent", false))))),
                Map.of("parameters", Map.of("method", "POST"))).size());
    }

    @Test
    void nestedExpandFilterAndMapMatchOperationSemantics() {
        String source = """
                distill(api, rule) {
                    return api.paths
                        .expand { path -> path.operationDetails
                            .filter { operation -> operation.method == rule.parameters.method
                                && regexSearch("(?i).*(create|update|delete|remove).*", path.path + " " + operation.summary) }
                            .map { operation -> occurrence(operation.pointer, operation.method + " " + path.path, "GET operation appears to mutate state") } };
                }
                """;
        Map<String, Object> api = api("""
                openapi: 3.0.0
                info: { title: Test, version: 1.0.0 }
                paths:
                  /customers:
                    get:
                      summary: Delete customer
                      responses: { '200': { description: OK } }
                """);

        assertEquals(List.of(new Diagnostic("/paths/~1customers/get", "GET /customers", "GET operation appears to mutate state")),
                runtime.execute(source, api, Map.of("parameters", Map.of("method", "GET"))));
    }

    @Test
    void operationSemanticsDistillScriptMatchesAllConfiguredModes() {
        String source = readResource("api-policy/matchers/operation-semantics/Matcher.dsl");
        Map<String, Object> api = api("""
                openapi: 3.0.0
                info: { title: Test, version: 1.0.0 }
                paths:
                  /customers:
                    get:
                      summary: Delete customer
                      responses: { '200': { description: OK } }
                  /customers/{customer_id}:
                    post:
                      summary: Replace customer
                      responses: { '200': { description: OK } }
                    put:
                      summary: Partially update customer
                      responses: { '200': { description: OK } }
                """);

        assertEquals(1, runtime.execute(source, api, Map.of("parameters", Map.of("method", "GET", "expected", "safe"))).size());
        assertEquals(1, runtime.execute(source, api, Map.of("parameters", Map.of("method", "POST", "match", "full-resource-replacement"))).size());
        assertEquals(1, runtime.execute(source, api, Map.of("parameters", Map.of("method", "PUT", "match", "partial-update"))).size());
        assertEquals(2, runtime.execute(source, api, Map.of("parameters", Map.of("match", "inconsistent-method-resource-semantics"))).size());
    }

    @Test
    void slashyRegexLiteralsAndMatchOperators() {
        // Bare /…/ in operand position, and the explicit ~/…/ alias.
        String source = """
                distill(api, rule) {
                    return api.paths
                        .filter { path -> path.path =~ /\\/v[0-9]+\\// && !(path.path ==~ ~/(?i).*internal.*/) }
                        .map { path -> occurrence(path.pointer, path.path, "Versioned public path") };
                }
                """;
        Map<String, Object> api = api("""
                openapi: 3.0.0
                info: { title: Test, version: 1.0.0 }
                paths:
                  /v1/customers: { get: { responses: { '200': { description: OK } } } }
                  /v2/internal/audit: { get: { responses: { '200': { description: OK } } } }
                  /health: { get: { responses: { '200': { description: OK } } } }
                """);

        assertEquals(List.of(new Diagnostic("/paths/~1v1~1customers", "/v1/customers", "Versioned public path")),
                runtime.execute(source, api, Map.of("parameters", Map.of())));
    }

    @Test
    void nestedExpandAndMapMatchResourcePaths() {
        String source = """
                distill(api, rule) {
                    return api.paths
                        .filter { path -> regexFullMatch("(?i).*/actions(?:/[^/]+)?", path.path) }
                        .expand { path -> path.operations
                            .map { method -> occurrence(path.pointer + "/" + method.lower(), method + " " + path.path, "Custom action resource is used") } };
                }
                """;
        Map<String, Object> api = api("""
                openapi: 3.0.0
                info: { title: Test, version: 1.0.0 }
                paths:
                  /customers/actions/archive:
                    post:
                      responses: { '200': { description: OK } }
                """);

        assertEquals(List.of(new Diagnostic("/paths/~1customers~1actions~1archive/post", "POST /customers/actions/archive", "Custom action resource is used")),
                runtime.execute(source, api, Map.of("parameters", Map.of("match", "custom-action"))));
    }

    @Test
    void filterAndMapMatchUriVersioning() {
        String source = """
                distill(api, rule) {
                    return api.paths
                        .filter { path -> regexFullMatch(".*/(v[0-9]+|version[0-9]+)(/.*)?", path.path) }
                        .map { path -> occurrence(path.pointer, path.path, "Interface version is exposed through uri") };
                }
                """;
        Map<String, Object> api = api("""
                openapi: 3.0.0
                info: { title: Test, version: 1.0.0 }
                paths:
                  /v1/customers:
                    get:
                      responses: { '200': { description: OK } }
                """);

        assertEquals(List.of(new Diagnostic("/paths/~1v1~1customers", "/v1/customers", "Interface version is exposed through uri")),
                runtime.execute(source, api, Map.of("parameters", Map.of("location", "uri", "match", "present"))));
    }

    @Test
    void listLiteralsTypeKeysAndTruthyBuiltins() {
        String source = """
                distill(api, rule) {
                    return api.paths
                        .filter { path -> ["string", "int"].any { t -> t == type(path.path) }
                            && path.keys.any { k -> k == "pointer" }
                            && truthy(path.path) }
                        .map { path -> occurrence(path.pointer, path.path, type(rule.parameters)) };
                }
                """;
        Map<String, Object> api = api("""
                openapi: 3.0.0
                info: { title: Test, version: 1.0.0 }
                paths:
                  /a: { get: { responses: { '200': { description: OK } } } }
                """);

        assertEquals(List.of(new Diagnostic("/paths/~1a", "/a", "dict")),
                runtime.execute(source, api, Map.of("parameters", Map.of())));
    }

    @Test
    void stringLiteralsThatLookLikeOperatorsAreNotOperators() {
        // "-", "+", ".", "==" etc. as string content must stay string literals.
        String source = """
                distill(api, rule) {
                    return api.paths
                        .filter { path -> path.path.contains("-")
                            && strip(path.path, "/") != ""
                            && join("-", ["a", "b"]) == "a-b" }
                        .map { path -> occurrence(path.pointer, path.path, "kept " + "-" + " ok") };
                }
                """;
        Map<String, Object> api = api("""
                openapi: 3.0.0
                info: { title: Test, version: 1.0.0 }
                paths:
                  /order-items: { get: { responses: { '200': { description: OK } } } }
                  /orders: { get: { responses: { '200': { description: OK } } } }
                """);

        assertEquals(List.of(new Diagnostic("/paths/~1order-items", "/order-items", "kept - ok")),
                runtime.execute(source, api, Map.of("parameters", Map.of())));
    }

    @Test
    void groupBucketsItemsByKeyInFirstSeenOrder() {
        // Flag every path whose first segment is shared by another path.
        String source = """
                distill(api, rule) {
                    return api.paths
                        .group { path -> pathSegments(path.path)[0] }
                        .values
                        .filter { g -> size(g) > 1 }
                        .expand { g -> g.map { path -> occurrence(path.pointer, path.path,
                            "shares a root with " + size(g) + " paths") } };
                }
                """;
        Map<String, Object> api = api("""
                openapi: 3.0.0
                info: { title: Test, version: 1.0.0 }
                paths:
                  /orders: { get: { responses: { '200': { description: OK } } } }
                  /orders/{id}: { get: { responses: { '200': { description: OK } } } }
                  /customers: { get: { responses: { '200': { description: OK } } } }
                """);

        assertEquals(List.of(
                        new Diagnostic("/paths/~1orders", "/orders", "shares a root with 2 paths"),
                        new Diagnostic("/paths/~1orders~1{id}", "/orders/{id}", "shares a root with 2 paths")),
                runtime.execute(source, api, Map.of("parameters", Map.of())));
    }

    private static Map<String, Object> api(String content) {
        return OpenApiMapAdapter.toMap(new OpenAPIV3Parser().readContents(content, null, new ParseOptions()).getOpenAPI());
    }

    private static String readResource(String path) {
        try (InputStream stream = DistillMatcherEvaluatorTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("missing resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
