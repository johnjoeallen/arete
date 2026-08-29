package com.speculate.validation.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FluentPolicyRuntimeTest {
    private final FluentPolicyRuntime runtime = new FluentPolicyRuntime();

    @Test
    void nestedExpandFilterAndMapMatchOperationSemantics() {
        String source = """
                detector(api, rule) {
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

        assertEquals(List.of(new Occurrence("/paths/~1customers/get", "GET /customers", "GET operation appears to mutate state")),
                runtime.execute(source, api, Map.of("parameters", Map.of("method", "GET"))));
    }

    @Test
    void nestedExpandAndMapMatchResourcePaths() {
        String source = """
                detector(api, rule) {
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

        assertEquals(List.of(new Occurrence("/paths/~1customers~1actions~1archive/post", "POST /customers/actions/archive", "Custom action resource is used")),
                runtime.execute(source, api, Map.of("parameters", Map.of("match", "custom-action"))));
    }

    @Test
    void filterAndMapMatchUriVersioning() {
        String source = """
                detector(api, rule) {
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

        assertEquals(List.of(new Occurrence("/paths/~1v1~1customers", "/v1/customers", "Interface version is exposed through uri")),
                runtime.execute(source, api, Map.of("parameters", Map.of("location", "uri", "match", "present"))));
    }

    private static Map<String, Object> api(String content) {
        return OpenApiMapAdapter.toMap(new OpenAPIV3Parser().readContents(content, null, new ParseOptions()).getOpenAPI());
    }
}
