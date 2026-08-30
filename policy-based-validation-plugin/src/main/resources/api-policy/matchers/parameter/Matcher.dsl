distill(api, rule) {
    return api.paths.expand { path -> path.operationDetails.expand { operation ->
        rule.parameters.check == "max-count"
            ? ((rule.parameters.maximum != null && size(operation.parameters) > rule.parameters.maximum)
                ? tokenize(",", "x").map { u -> occurrence(operation.pointer,
                    operation.method + " " + path.path,
                    "Operation declares " + size(operation.parameters)
                        + " parameters, more than the maximum of " + rule.parameters.maximum) }
                : tokenize(",", "x").filter { u -> false })
        : rule.parameters.check == "path-required"
            ? operation.parameters
                .filter { param -> param.in == "path" && !param.required }
                .map { param -> occurrence(param.pointer,
                    operation.method + " " + path.path + " " + param.name,
                    "Path parameter '" + param.name + "' is not marked required") }
        : rule.parameters.check == "schema-present"
            ? operation.parameters
                .filter { param -> !param.schemaPresent }
                .map { param -> occurrence(param.pointer,
                    operation.method + " " + path.path + " " + param.name,
                    "Parameter '" + param.name + "' defines neither a schema nor content") }
        : rule.parameters.check == "unique"
            ? operation.parameters
                .group { param -> param.in + " " + param.name }
                .values
                .filter { dupes -> size(dupes) > 1 }
                .expand { dupes -> enumerate(dupes)
                    .filter { indexed -> indexed[0] > 0 }
                    .map { indexed -> occurrence(indexed[1].pointer,
                        operation.method + " " + path.path + " " + indexed[1].name,
                        "Parameter '" + dupes[0].name + "' in " + dupes[0].in
                            + " is declared more than once") } }
        : rule.parameters.check == "template-match"
            ? operation.parameters
                .filter { param -> param.in == "path"
                    && !path.templateParameters.any { t -> t == param.name } }
                .map { param -> occurrence(operation.pointer,
                    operation.method + " " + path.path,
                    "Path parameter '" + param.name + "' has no matching {placeholder} in the path template") }
              + path.templateParameters
                .filter { name -> !operation.parameters.any { param -> param.in == "path" && param.name == name } }
                .map { name -> occurrence(operation.pointer,
                    operation.method + " " + path.path,
                    "Path template placeholder '{" + name + "}' has no matching path parameter") }
        : tokenize(",", "x").filter { u -> false } } };
}
