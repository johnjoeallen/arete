distill(api, rule) {
    return rule.parameters["check"] == "unreferenced-schema"
        ? api.schemas
            .filter { schema -> !api.lint.refs.any { ref ->
                ref == "#/components/schemas/" + schema.name } }
            .map { schema -> occurrence(schema.pointer, schema.name,
                "Component schema '" + schema.name + "' is defined but never referenced") }
        : [];
}
