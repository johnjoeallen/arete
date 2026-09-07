distill(api, rule) {
    return api.paths.expand { path -> path.operationDetails.expand { operation -> operation.responses
        .filter { resp -> ("" + resp.status) == ("" + rule.parameters["status"])
            && (rule.parameters["required"]
                ? !(rule.parameters["headers"] != null
                    ? tokenize(",", rule.parameters["headers"]).map { t -> t.trim() }.filter { t -> t != "" }
                    : tokenize(",", "" + rule.parameters["header"]))
                    .all { e -> resp.headers.any { h -> h.lower() == e.lower() } }
                : (rule.parameters["headers"] != null
                    ? tokenize(",", rule.parameters["headers"]).map { t -> t.trim() }.filter { t -> t != "" }
                    : tokenize(",", "" + rule.parameters["header"]))
                    .any { e -> resp.headers.any { h -> h.lower() == e.lower() } }) }
        .map { resp -> occurrence(operation.pointer, operation.method + " " + path.path,
            "Response " + resp.status + " "
            + (rule.parameters["required"] ? "lacks one or more required" : "contains an unexpected")
            + " headers: "
            + join(", ", rule.parameters["headers"] != null
                ? tokenize(",", rule.parameters["headers"]).map { t -> t.trim() }.filter { t -> t != "" }
                : tokenize(",", "" + rule.parameters["header"]))) } } };
}
