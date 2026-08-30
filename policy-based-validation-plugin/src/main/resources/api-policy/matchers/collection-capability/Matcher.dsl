distill(api, rule) {
    return rule.scope == "operation"
        ? api.paths.expand { path -> path.operationDetails
            .filter { operation -> operation.method == "GET" && !path.path.contains("{")
                && size(operation.parameters.filter { param -> param.in == "query"
                    && param.name =~ ("(?i)" + rule.parameters["name-pattern"]) }) == 0 }
            .map { operation -> diagnostic(operation.pointer,
                operation.method + " " + path.path,
                "Collection operation lacks the configured query capability") } }
        : api.paths.expand { path -> path.operationDetails.expand { operation -> operation.parameters
            .filter { param -> param.in == "query"
                && param.name =~ ("(?i)" + rule.parameters["name-pattern"])
                && ((rule.parameters["check"] == "string" && param.schemaType != "string")
                    || (rule.parameters["check"] == "array" && param.schemaType != "array")
                    || (rule.parameters["check"] == "form" && param.style != null && param.style != "form")) }
            .map { param -> diagnostic(param.pointer,
                operation.method + " " + path.path,
                "Collection query capability does not use the configured representation: " + param.name) } } };
}
