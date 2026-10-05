distill(api, rule) {
    return api.schemas
        .expand { schema -> schema.properties
            .filter { prop ->
                (prop.name == "id" && prop.type != "string")
                || ((prop.name == "created" || prop.name == "modified")
                    && !(prop.type == "string" && prop.format == "date-time")) }
            .map { prop -> occurrence(prop.pointer, prop.name,
                "Common field has an inconsistent type or format") } };
}
