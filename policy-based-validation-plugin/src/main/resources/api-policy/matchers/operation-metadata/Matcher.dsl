distill(api, rule) {
    return rule.parameters["check"] == "tags-present"
        ? api.paths.expand { path -> path.operationDetails
            .filter { op -> size(op.tags) == 0 }
            .map { op -> occurrence(op.pointer, op.method + " " + path.path,
                "Operation is not assigned any tag") } }
        : rule.parameters["check"] == "unique-operation-id"
        ? api.paths
            .expand { path -> path.operationDetails.map { op ->
                [op.pointer, op.method + " " + path.path, op.operationId] } }
            .group { entry -> "" + entry[2] }
            .values
            .expand { group ->
                (group[0][2] is blank)
                    ? group.map { entry -> occurrence(entry[0], entry[1],
                        "Operation has no operationId") }
                    : (size(group) > 1
                        ? enumerate(group)
                            .filter { indexed -> indexed[0] > 0 }
                            .map { indexed -> occurrence(indexed[1][0], indexed[1][1],
                                "operationId '" + group[0][2] + "' is also used by " + group[0][1]) }
                        : []) }
        : [];
}
