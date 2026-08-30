distill(api, rule) {
    return rule.parameters["response-shape"] == "json-object"
        ? api.paths.expand { path -> path.operationDetails.expand { operation -> operation.responses
            .filter { resp -> parseInt(resp.status, -1) >= 200 && parseInt(resp.status, -1) < 300
                && size(resp.schemaTypes) > 0
                && resp.schemaTypes.any { t -> t != "object" } }
            .map { resp -> occurrence(path.pointer, operation.method + " " + path.path,
                "Successful response is not a JSON object") } } }
        : rule.parameters["error-format"] == "problem-json"
        ? api.paths.expand { path -> path.operationDetails.expand { operation -> operation.responses
            .filter { resp -> parseInt(resp.status, -1) >= 400 && parseInt(resp.status, -1) < 600
                && !operation.mediaTypes.any { m -> m == "application/problem+json" } }
            .map { resp -> occurrence(path.pointer, operation.method + " " + path.path,
                "Error response does not declare application/problem+json") } } }
        : rule.scope == "operation"
        ? api.paths.expand { path -> path.operationDetails
            .filter { operation ->
                !(rule.parameters["operation-type"] == "create"
                    && !(operation.method == "POST" || operation.method == "PUT"))
                && !(rule.parameters["operation-type"] == "identifiable-resource-retrieval"
                    && !(operation.method == "GET" && path.path.contains("{")))
                && rule.parameters["required-status"] != null
                && !operation.responses.any { r -> parseInt(r.status, -1) == rule.parameters["required-status"] } }
            .map { operation -> occurrence(operation.pointer, operation.method + " " + path.path,
                "Operation lacks the required documented status") } }
        : api.paths.expand { path -> path.operationDetails.expand { operation -> operation.responses
            .filter { resp ->
                (rule.parameters["status"] == null || parseInt(resp.status, -1) == rule.parameters["status"])
                && (rule.parameters["match"] == "semantic-conflict"
                    ? (parseInt(resp.status, -1) >= 200 && parseInt(resp.status, -1) < 300
                        && (resp.description == null ? "" : resp.description)
                            ==~ /(?i).*\\b(error|failure|failed|invalid)\\b.*/)
                    : rule.parameters["status"] != null) }
            .map { resp -> occurrence(operation.pointer, operation.method + " " + path.path,
                rule.parameters["match"] == "semantic-conflict"
                    ? "Status code conflicts with response semantics"
                    : "Response uses the configured status code") } } };
}
