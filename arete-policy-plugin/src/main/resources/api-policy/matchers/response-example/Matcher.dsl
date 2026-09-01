distill(api, rule) {
    return rule.parameters["check"] != "unique-error-payloads"
        ? []
        : api.paths.expand { path -> path.operationDetails.expand { operation ->
            operation.responses
                .filter { resp -> parseInt(resp.status, -1) >= 400 && parseInt(resp.status, -1) < 600 }
                .expand { resp -> (resp.exampleStrings == null ? [] : resp.exampleStrings)
                    .map { example -> ["" + resp.status, example] } }
                .group { pair -> pair[1] }
                .values
                .expand { group -> enumerate(group)
                    .filter { indexed -> indexed[0] > 0 }
                    .map { indexed -> occurrence(operation.pointer, operation.method + " " + path.path,
                        "Error responses " + group[0][0] + " and " + indexed[1][0]
                          + " share an identical example payload") } } } };
}
