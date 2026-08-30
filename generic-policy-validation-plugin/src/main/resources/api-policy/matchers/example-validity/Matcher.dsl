distill(api, rule) {
    return rule.parameters["check"] == "covers-required"
        ? api.schemas.expand { schema -> type(schema["example"]) != "dict"
            ? []
            : (schema.requiredFields == null ? [] : schema.requiredFields)
                .filter { field -> !schema["example"].keys.any { k -> k == field } }
                .map { field -> diagnostic(schema.pointer, schema.name,
                    "Schema example omits the required field '" + field + "'") } }
        : rule.parameters["check"] == "satisfies-constraints"
        ? api.schemas.expand { schema -> schema.properties.expand { prop ->
            !truthy(prop.examplePresent)
                ? []
                : (truthy(prop.pattern) && type(prop.example) == "string"
                        && !regexSearch(prop.pattern, prop.example)
                    ? [diagnostic(prop.pointer, schema.name + "." + prop.name,
                        "Example does not match pattern " + prop.pattern)] : [])
                + (type(prop.example) == "string" && prop.minLength != null
                        && prop.example.length < prop.minLength
                    ? [diagnostic(prop.pointer, schema.name + "." + prop.name,
                        "Example is shorter than minLength " + ("" + prop.minLength))] : [])
                + (type(prop.example) == "string" && prop.maxLength != null
                        && prop.example.length > prop.maxLength
                    ? [diagnostic(prop.pointer, schema.name + "." + prop.name,
                        "Example is longer than maxLength " + ("" + prop.maxLength))] : [])
                + (["int", "float"].any { t -> t == type(prop.example) }
                    ? ((prop.minimum != null && ["int", "float"].any { t -> t == type(prop.minimum) }
                        ? (truthy(prop.exclusiveMinimum) && prop.example <= prop.minimum
                            ? [diagnostic(prop.pointer, schema.name + "." + prop.name,
                                "Example is not greater than exclusive minimum " + ("" + prop.minimum))]
                            : (!truthy(prop.exclusiveMinimum) && prop.example < prop.minimum
                                ? [diagnostic(prop.pointer, schema.name + "." + prop.name,
                                    "Example is below minimum " + ("" + prop.minimum))] : []))
                        : [])
                      + (prop.maximum != null && ["int", "float"].any { t -> t == type(prop.maximum) }
                        ? (truthy(prop.exclusiveMaximum) && prop.example >= prop.maximum
                            ? [diagnostic(prop.pointer, schema.name + "." + prop.name,
                                "Example is not less than exclusive maximum " + ("" + prop.maximum))]
                            : (!truthy(prop.exclusiveMaximum) && prop.example > prop.maximum
                                ? [diagnostic(prop.pointer, schema.name + "." + prop.name,
                                    "Example exceeds maximum " + ("" + prop.maximum))] : []))
                        : []))
                    : [])
                + (truthy(prop.enumPresent)
                        && ["string", "int", "float", "bool"].any { t -> t == type(prop.example) }
                    ? (!prop.enumValues.map { v -> "" + v }.any { a -> a == ("" + prop.example) }
                        ? [diagnostic(prop.pointer, schema.name + "." + prop.name,
                            "Example is not one of the declared enum values")] : [])
                    : []) } }
        : [];
}
