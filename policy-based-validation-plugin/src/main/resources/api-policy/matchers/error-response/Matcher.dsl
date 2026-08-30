distill(api, rule) {
    return rule.scope == "operation"
        ? api.paths.expand { path -> path.operationDetails
            .filter { operation -> rule.parameters["required-class"] != null
                && !operation.responses.any { r ->
                    (rule.parameters["required-class"] == "success"
                        && parseInt(r.status, -1) >= 200 && parseInt(r.status, -1) < 300)
                    || (rule.parameters["required-class"] == "client-error"
                        && parseInt(r.status, -1) >= 400 && parseInt(r.status, -1) < 500)
                    || (rule.parameters["required-class"] == "server-error"
                        && parseInt(r.status, -1) >= 500 && parseInt(r.status, -1) < 600) } }
            .map { operation -> occurrence(operation.pointer, operation.method + " " + path.path,
                "Operation does not document a " + rule.parameters["required-class"] + " response") } }
        : api.paths.expand { path -> path.operationDetails.expand { operation -> operation.responses
            .filter { resp -> (rule.parameters["status"] == null
                    || parseInt(resp.status, -1) == rule.parameters["status"])
                && ((rule.parameters["require-description"] != null
                        && parseInt(resp.status, -1) >= 400 && parseInt(resp.status, -1) < 600
                        && !resp.description)
                    || (rule.parameters["problem-json"] != null
                        && parseInt(resp.status, -1) >= 400 && parseInt(resp.status, -1) < 600
                        && !resp.mediaTypes.any { m -> m == "application/problem+json" })
                    || (rule.parameters["required-header"] != null
                        && !resp.headers.any { h -> h.lower() == rule.parameters["required-header"].lower() })) }
            .map { resp ->
                (rule.parameters["require-description"] != null
                        && parseInt(resp.status, -1) >= 400 && parseInt(resp.status, -1) < 600
                        && !resp.description)
                    ? occurrence(operation.pointer, operation.method + " " + path.path,
                        "Error response is missing a description")
                    : (rule.parameters["problem-json"] != null
                            && parseInt(resp.status, -1) >= 400 && parseInt(resp.status, -1) < 600
                            && !resp.mediaTypes.any { m -> m == "application/problem+json" })
                        ? occurrence(operation.pointer, operation.method + " " + path.path,
                            "Error response does not declare application/problem+json")
                        : occurrence(operation.pointer, operation.method + " " + path.path,
                            "Response " + resp.status + " is missing " + rule.parameters["required-header"] + "") } } };
}
