distill(api, rule) {
    return api.schemas.expand { schema ->
        (schema.array && !truthy(schema.itemsPresent)
            ? [occurrence(schema.pointer, schema.name,
                "Array schema '" + schema.name + "' declares no items")]
            : [])
        + schema.properties
            .filter { prop -> prop.array && !truthy(prop.itemsPresent) }
            .map { prop -> occurrence(prop.pointer, schema.name + "." + prop.name,
                "Array property '" + prop.name + "' declares no items") } };
}
