distill(api, rule) {
    return api.schemas
        .expand { schema -> schema.properties
            .filter { prop -> prop.type == "string"
                && prop.format == "date-time"
                && !prop.name.endsWith(rule.parameters.suffix) }
            .map { prop -> diagnostic(prop.pointer, prop.name,
                "Date-time property name does not end with " + rule.parameters.suffix) } };
}
