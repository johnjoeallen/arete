distill(api, rule) {
    return rule.scope == "operation"
        ? api.paths.expand { path -> path.operationDetails
            .filter { operation ->
                ((operation.security != null ? operation.security : api.security) != null
                    && count(operation.security != null ? operation.security : api.security) > 0)
                && rule.parameters["required-status"] != null
                && !operation.responses.any { r -> parseInt(r.status, -1) == rule.parameters["required-status"] } }
            .map { operation -> occurrence(operation.pointer, operation.method + " " + path.path,
                "Secured operation does not document response " + rule.parameters["required-status"]) } }
        : api.paths.expand { path -> path.operationDetails.expand { operation -> operation.responses
            .expand { resp ->
                (rule.parameters["required-status"] != null
                        && parseInt(resp.status, -1) != rule.parameters["required-status"])
                    ? tokenize(",", "x").filter { u -> false }
                    : (rule.parameters["required-header"] != null
                        && !resp.headers.any { h -> h.lower() == rule.parameters["required-header"].lower() }
                        ? tokenize(",", "x").map { u -> occurrence(operation.pointer,
                            operation.method + " " + path.path,
                            "Authentication response is missing " + rule.parameters["required-header"]) }
                        : tokenize(",", "x").filter { u -> false })
                    + (rule.parameters["forbidden-header"] != null
                        && resp.headers.any { h -> h.lower() == rule.parameters["forbidden-header"].lower() }
                        ? tokenize(",", "x").map { u -> occurrence(operation.pointer,
                            operation.method + " " + path.path,
                            "Authorization response must not include " + rule.parameters["forbidden-header"]) }
                        : tokenize(",", "x").filter { u -> false }) } } };
}
