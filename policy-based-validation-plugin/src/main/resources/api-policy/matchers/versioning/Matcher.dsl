distill(api, rule) {
    return rule.parameters["match"] == "absent"
        ? (api.paths.any { path ->
            path.path ==~ /.*\/(v[0-9]+|version[0-9]+)(\/.*)?/
            || path.operationDetails.any { operation ->
                operation.parameters.any { param -> param.in == "header"
                    && param.name ==~ /(?i)(api[-_])?version|x-api-version/ }
                || operation.mediaTypes.any { mt -> mt ==~ /(?i).*\+?v[0-9]+.*|.*version[0-9]+.*/ } } }
            ? api.paths.filter { p -> false }
            : tokenize(",", "x").map { u -> diagnostic("/paths", "API",
                "Interface has no explicit versioning") })
        : api.paths
            .filter { path ->
                rule.parameters["location"] == "uri"
                    ? path.path ==~ /.*\/(v[0-9]+|version[0-9]+)(\/.*)?/
                : rule.parameters["location"] == "header"
                    ? path.operationDetails.any { operation -> operation.parameters.any { param ->
                        param.in == "header" && param.name ==~ /(?i)(api[-_])?version|x-api-version/ } }
                : rule.parameters["location"] == "media-type"
                    ? path.operationDetails.any { operation -> operation.mediaTypes.any { mt ->
                        mt ==~ /(?i).*\+?v[0-9]+.*|.*version[0-9]+.*/ } }
                : false }
            .map { path -> diagnostic(path.pointer, path.path,
                "Interface version is exposed through " + rule.parameters["location"]) };
}
