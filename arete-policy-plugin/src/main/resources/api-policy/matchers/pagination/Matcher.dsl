distill(api, rule) {
    return rule.scope == "operation"
        ? api.paths.expand { path -> path.operationDetails
            .filter { operation -> operation.method == "GET" && !path.path.contains("{")
                && count(operation.parameters.filter { p -> p.in == "query"
                    && p.name =~ ("(?i)" + rule.parameters["name-pattern"]) }) == 0 }
            .map { operation -> occurrence(operation.pointer, operation.method + " " + path.path,
                "Collection operation lacks the configured pagination control") } }
        : rule.scope == "query-parameter"
        ? api.paths.expand { path -> path.operationDetails.expand { operation -> operation.parameters
            .filter { p -> p.in == "query" && p.name =~ ("(?i)" + rule.parameters["name-pattern"])
                && ((rule.parameters["check"] == "integer" && p.schemaType != "integer")
                    || (rule.parameters["check"] == "string" && p.schemaType != "string")
                    || (rule.parameters["check"] == "maximum"
                        && (p.schemaMaximum == null || p.schemaMaximum > rule.parameters["maximum"]))) }
            .map { p -> occurrence(p.pointer, operation.method + " " + path.path,
                "Pagination parameter does not meet the configured constraint: " + p.name) } } }
        : api.paths.expand { path -> path.operationDetails.expand { operation -> operation.responses
            .filter { resp -> operation.method == "GET" && !path.path.contains("{")
                && parseInt(resp.status, -1) >= 200 && parseInt(resp.status, -1) < 300
                && !resp.headers.any { h -> h.lower() == "link" } }
            .map { resp -> occurrence(operation.pointer, operation.method + " " + path.path,
                "Successful paginated response lacks a Link header") } } };
}
