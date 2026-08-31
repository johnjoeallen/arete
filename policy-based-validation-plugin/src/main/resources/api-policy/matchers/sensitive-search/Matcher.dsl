distill(api, rule) {
    return rule.scope == "query-parameter"
        ? api.paths.expand { path -> path.operationDetails.expand { operation -> operation.parameters
            .filter { param -> param.in == "query"
                && param.name =~ ("(?i)" + rule.parameters["search-pattern"]) }
            .map { param -> occurrence(param.pointer,
                operation.method + " " + path.path,
                "Search query parameter may carry sensitive data: " + param.name) } } }
        : api.paths.expand { path -> path.operationDetails.expand { operation ->
            (count(operation.parameters.filter { param -> param.in == "query"
                    && param.name =~ ("(?i)" + rule.parameters["search-pattern"]) }) > 0
             && count(operation.parameters.filter { param -> param.in == "query"
                    && param.name =~ ("(?i)" + rule.parameters["sensitive-pattern"]) }) > 0)
                ? tokenize(",", "x").map { u -> occurrence(operation.pointer,
                    operation.method + " " + path.path,
                    "Operation permits searching sensitive query data") }
                : tokenize(",", "x").filter { u -> false } } };
}
