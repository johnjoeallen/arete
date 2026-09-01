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
    void stringLiteralInterpolatesDoubleBraceHoles() {
        assertEquals(List.of(new Diagnostic("/", "x", "prefix-Widget-v2")), runtime.execute("""
                distill(api, rule) { return [occurrence("/", "x", "prefix-{{rule.parameters.name}}-{{rule.parameters.suffix}}")]; }
                """,
                Map.of(),
                Map.of("parameters", Map.of("name", "Widget", "suffix", "v2"))));
    }

    @Test
    void regexLiteralInterpolatesDoubleBraceHolesIncludingQuotedSubExpressions() {
        // The alternation is built from a rule parameter — the hole holds a string
        // literal, whose quotes are not the enclosing regex/string delimiters.
        List<Diagnostic> found = runtime.execute("""
                distill(api, rule) {
                    return api.values
                        .filter { v -> v ==~ /({{join("|", tokenize(",", rule.parameters["verbs"]))}}) .*/ }
                        .map { v -> occurrence("/", v, "verb") };
                }
                """,
                Map.of("values", List.of("Fetch a widget", "Get a widget", "Delete a widget")),
                Map.of("parameters", Map.of("verbs", "Fetch,Delete")));
        assertEquals(List.of(
                new Diagnostic("/", "Fetch a widget", "verb"),
                new Diagnostic("/", "Delete a widget", "verb")), found);
    }

    @Test
    void stringPredicatesAcceptAListMeaningAnyOf() {
        String dsl = "distill(api, rule) { return api.values"
                + ".filter { v -> v.startsWith(rule.parameters[\"p\"]) }"
                + ".map { v -> occurrence(\"/\", v, \"hit\") }; }";
        List<String> values = List.of("Get a widget", "Delete it", "List all", "Fetch one");
        assertEquals(List.of(new Diagnostic("/", "Get a widget", "hit"), new Diagnostic("/", "Delete it", "hit")),
                runtime.execute(dsl, Map.of("values", values), Map.of("parameters", Map.of("p", List.of("Get", "Delete")))));

        // a plain string argument still works
        assertEquals(List.of(new Diagnostic("/", "List all", "hit")), runtime.execute(
                "distill(api, rule) { return api.values.filter { v -> v.endsWith([\"all\", \"none\"]) }.map { v -> occurrence(\"/\", v, \"hit\") }; }",
                Map.of("values", values), Map.of("parameters", Map.of())));
        assertEquals(2, runtime.execute(
                "distill(api, rule) { return api.values.filter { v -> v.contains([\"wid\", \"one\"]) }.map { v -> occurrence(\"/\", v, \"hit\") }; }",
                Map.of("values", values), Map.of("parameters", Map.of())).size());
    }

    @Test
    void startsWithWordIsWordAwareUnlikeStartsWith() {
        Map<String, Object> values = Map.of("values", List.of("List all", "Listing all", "List.", "List"));
        Map<String, Object> rule = Map.of("parameters", Map.of("p", List.of("List")));
        // startsWithWord: whole word — "List all", "List.", "List" (not "Listing all")
        assertEquals(List.of(new Diagnostic("/", "List all", "x"), new Diagnostic("/", "List.", "x"), new Diagnostic("/", "List", "x")),
                runtime.execute(
                        "distill(api, rule) { return api.values.filter { v -> v.startsWithWord(rule.parameters[\"p\"]) }.map { v -> occurrence(\"/\", v, \"x\") }; }",
                        values, rule));
        // startsWith: raw prefix — "Listing all" also matches
        assertEquals(4, runtime.execute(
                "distill(api, rule) { return api.values.filter { v -> v.startsWith(rule.parameters[\"p\"]) }.map { v -> occurrence(\"/\", v, \"x\") }; }",
                values, rule).size());
    }

    @Test
    void endsWithWordIsWordAware() {
        // "order Id", "order-Id", "the Id" land on a boundary; "orderId" does not.
        assertEquals(3, runtime.execute(
                "distill(api, rule) { return api.values.filter { v -> v.endsWithWord([\"Id\"]) }.map { v -> occurrence(\"/\", v, \"x\") }; }",
                Map.of("values", List.of("order Id", "order-Id", "orderId", "the Id")),
                Map.of("parameters", Map.of())).size());
    }

    @Test
    void checksConcatenatesRepeatableFilterMapStanzasOverOneBoundSource() {
        // The source (api.values, blank-filtered) is bound once; each comma-separated
        // stanza is a bare filter{}.map{} rooted at it and using the implicit `it`;
        // results concatenate. Two stanzas both match "aaa" -> two occurrences.
        List<Diagnostic> found = runtime.execute("""
                distill(api, rule) {
                    return checks(api.values.filter { it != "" }) {
                        filter { it.startsWith("a") }.map { occurrence("/", it, "starts-a") },
                        filter { it.length > 2 }.map { occurrence("/", it, "long") }
                    };
                }
                """,
                Map.of("values", List.of("aaa", "b", "")),
                Map.of("parameters", Map.of()));
        assertEquals(List.of(
                new Diagnostic("/", "aaa", "starts-a"),
                new Diagnostic("/", "aaa", "long")), found);
    }

    @Test
    void wordsSplitsProseIntoSubstantiveTokens() {
        // "Get  the widget — v2!" -> [Get, the, widget, v2] : whitespace runs collapse,
        // the em dash drops (no letter), trailing "!" is stripped. "-" alone has no word.
        assertEquals(1, runtime.execute(
                "distill(api, rule) { return api.values.filter { v -> count(words(v)) == 4 }.map { v -> occurrence(\"/\", \"v\", \"4\") }; }",
                Map.of("values", java.util.List.of("Get  the widget — v2!", "one two")),
                Map.of("parameters", Map.of())).size());
        // too few words
        assertEquals(1, runtime.execute(
                "distill(api, rule) { return api.values.filter { v -> count(words(v)) < 3 }.map { v -> occurrence(\"/\", \"v\", \"few\") }; }",
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
                        .filter { g -> count(g) > 1 }
                        .expand { g -> g.map { path -> occurrence(path.pointer, path.path,
                            "shares a root with " + count(g) + " paths") } };
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
