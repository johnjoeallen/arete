distill(api, rule) {
    return rule.parameters.location == "request"
        ? api.paths.expand { path -> path.operationDetails
            .filter { operation ->
                rule.parameters.match == "absent"
                    ? operation.requestMediaTypes.count { t -> true } == 0
                    : rule.parameters.match == "wildcard"
                        ? operation.requestMediaTypes.any { t -> t == "*/*" || t.endsWith("/*") || t.contains("*") }
                        : rule.parameters.match == "not-allowed"
                            ? operation.requestMediaTypes.any { t -> !(tokenize(",", rule.parameters.allowed).any { a -> a.trim().lower() == t.lower() }) }
                            : false }
            .map { operation -> diagnostic(operation.pointer,
                operation.method + " " + path.path,
                "Request body media type " + rule.parameters.match) } }
        : api.paths.expand { path -> path.operationDetails.expand { operation -> operation.responses
            .filter { response ->
                rule.parameters.match == "absent"
                    ? response.mediaTypes.count { t -> true } == 0
                    : rule.parameters.match == "wildcard"
                        ? response.mediaTypes.any { t -> t == "*/*" || t.endsWith("/*") || t.contains("*") }
                        : rule.parameters.match == "not-allowed"
                            ? response.mediaTypes.any { t -> !(tokenize(",", rule.parameters.allowed).any { a -> a.trim().lower() == t.lower() }) }
                            : false }
            .map { response -> diagnostic(operation.pointer,
                operation.method + " " + path.path,
                "Response " + response.status + " media type " + rule.parameters.match) } } };
}
