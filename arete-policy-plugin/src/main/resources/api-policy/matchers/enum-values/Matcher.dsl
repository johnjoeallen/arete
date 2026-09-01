distill(api, rule) {
    return api.schemas.expand { schema -> schema.properties
        .filter { prop -> prop.enumPresent
            && rule.parameters["check"] == "no-duplicates"
            && count(prop.enumValues) != count(distinct(prop.enumValues)) }
        .map { prop -> occurrence(prop.pointer, prop.name,
            "Enum for '" + prop.name + "' contains duplicate values") } };
}
