distill(api, rule) {
    return (rule.scope == "property"
            ? api.schemas.expand { schema -> schema.properties }
        : rule.scope == "schema"
            ? api.schemas
        : rule.scope == "path-segment"
            ? api.paths.expand { path -> path.segments }
        : rule.scope == "path-parameter"
            ? api.paths.expand { path -> path.operationDetails.expand { op -> op.parameters.filter { pm -> pm.in == "path" } } }
        : rule.scope == "query-parameter"
            ? api.paths.expand { path -> path.operationDetails.expand { op -> op.parameters.filter { pm -> pm.in == "query" } } }
        : rule.scope == "header"
            ? api.paths.expand { path -> path.operationDetails.expand { op -> op.parameters.filter { pm -> pm.in == "header" } } }
        : api.schemas.filter { x -> false })
        .filter { candidate -> !(
            (rule.parameters["convention"] != null && rule.parameters["match"] == "non-conforming"
                && (rule.parameters["convention"] == "camelCase" ? candidate.name ==~ /[a-z][A-Za-z0-9]*/
                    : rule.parameters["convention"] == "snake_case" ? candidate.name ==~ /[a-z][a-z0-9]*(?:_[a-z0-9]+)*/
                    : rule.parameters["convention"] == "kebab-case" ? candidate.name ==~ /[a-z][a-z0-9]*(?:-[a-z0-9]+)*/
                    : rule.parameters["convention"] == "hyphenated" ? candidate.name ==~ /[A-Za-z0-9]+(?:-[A-Za-z0-9]+)+/
                    : false))
            || (rule.parameters["match"] == "unsupported-character" && candidate.name ==~ /[A-Za-z][A-Za-z0-9_-]*/)
            || (rule.parameters["suffix"] != null && rule.parameters["match"] == "present"
                && !candidate.name.endsWith(rule.parameters["suffix"]))
            || (rule.parameters["semantic"] == "collection" && candidate.name.lower().endsWith("s") && candidate.name.length > 1)
            || (rule.parameters["semantic"] == "singular" && candidate.name.lower().endsWith("s") && candidate.name.length > 1)
            || (rule.parameters["semantic"] == "plural" && !(candidate.name.lower().endsWith("s") && candidate.name.length > 1))
            || (rule.parameters["schema-type"] == "array" && candidate.type != "array")) }
        .map { candidate -> diagnostic(candidate.pointer, candidate.name,
            rule.parameters["suffix"] != null ? "Name has prohibited suffix " + rule.parameters["suffix"]
                : rule.parameters["semantic"] == "collection" ? "Collection name is singular"
                : rule.parameters["semantic"] == "singular" ? "Array property has a singular name"
                : rule.parameters["match"] == "unsupported-character" ? "Name contains unsupported characters"
                : "Name does not use the configured convention") };
}
