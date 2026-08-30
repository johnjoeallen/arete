distill(api, rule) {
    return rule.parameters["match"] == "trailing-slash"
        ? api.paths
            .filter { path -> path.path.length > 1 && path.path.endsWith("/") }
            .map { path -> diagnostic(path.pointer, path.path,
                "Resource path has an unnecessary trailing slash") }
        : rule.parameters["match"] == "embedded-identifier"
        ? api.paths
            .filter { path -> tokenize("/", path.path).any { s ->
                !s.startsWith("{") && s ==~ /.*(?:Id|ID|[0-9]{2,}).*/ } }
            .map { path -> diagnostic(path.pointer, path.path,
                "Resource identifier is embedded in a path segment") }
        : api.paths
            .filter { path ->
                rule.parameters["match"] == "operation-verb"
                    ? last(tokenize("/", path.path)).lower() ==~ /(get|list|create|update|delete|remove|add|set).*/
                : rule.parameters["match"] == "query-predicate"
                    ? last(tokenize("/", path.path)) ==~ /(?i).*(find|get|search)By[A-Z].*/
                : rule.parameters["match"] == "rpc-style"
                    ? size(tokenize("/", path.path)) > 1
                      && last(tokenize("/", path.path)).lower() ==~ /(get|list|create|update|delete|remove|add|set)/
                : rule.parameters["match"] == "custom-action"
                    ? path.path ==~ /(?i).*\/actions(?:\/[^\/]+)?/
                : rule.parameters["match"] == "action-style"
                    ? (path.path ==~ /(?i).*\/actions(?:\/[^\/]+)?/
                       || last(tokenize("/", path.path)).lower() ==~ /(get|list|create|update|delete|remove|add|set).*/)
                : false }
            .expand { path -> path.operations
                .map { method -> diagnostic(path.pointer + "/" + method.lower(),
                    method + " " + path.path,
                    rule.parameters["match"] == "query-predicate" ? "Resource path contains a query predicate"
                        : rule.parameters["match"] == "rpc-style" ? "API uses RPC-style resource design"
                        : rule.parameters["match"] == "custom-action" ? "Custom action resource is used"
                        : rule.parameters["match"] == "action-style" ? "Action-style endpoint is used"
                        : "Resource path contains an operation verb") } };
}
