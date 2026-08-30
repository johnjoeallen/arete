distill(api, rule) {
    return rule.scope == "property"
        ? api.schemas.expand { schema -> schema.properties
            .filter { prop ->
                (rule.parameters.require != "example"
                    && prop.description is blank)
                || (rule.parameters.require != "description" && !prop.examplePresent) }
            .map { prop -> occurrence(prop.pointer, schema.name + "." + prop.name,
                (rule.parameters.require != "example"
                        && prop.description is blank
                        && rule.parameters.require != "description" && !prop.examplePresent)
                    ? schema.name + "." + prop.name + " has no description or example"
                    : (rule.parameters.require != "example"
                            && prop.description is blank)
                        ? schema.name + "." + prop.name + " has no description"
                        : schema.name + "." + prop.name + " has no example") } }
        : api.paths.expand { path -> path.operationDetails.expand { operation -> operation.parameters
            .filter { param ->
                (rule.parameters.require != "example"
                    && param.description is blank)
                || (rule.parameters.require != "description" && !param.examplePresent) }
            .map { param -> occurrence(param.pointer,
                operation.method + " " + path.path + " " + param.name,
                (rule.parameters.require != "example"
                        && param.description is blank
                        && rule.parameters.require != "description" && !param.examplePresent)
                    ? operation.method + " " + path.path + " " + param.name + " has no description or example"
                    : (rule.parameters.require != "example"
                            && param.description is blank)
                        ? operation.method + " " + path.path + " " + param.name + " has no description"
                        : operation.method + " " + path.path + " " + param.name + " has no example") } } };
}
