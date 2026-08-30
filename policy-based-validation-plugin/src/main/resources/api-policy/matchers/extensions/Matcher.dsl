distill(api, rule) {
    return api.info.extensionKeys
            .filter { key -> !tokenize(",", "" + rule.parameters["allowed"]).any { a -> a.trim() == key } }
            .map { key -> diagnostic("/info", "info", "Uses the non-standard extension '" + key + "'") }
        + api.paths.expand { path -> path.operationDetails.expand { operation -> operation.extensionKeys
            .filter { key -> !tokenize(",", "" + rule.parameters["allowed"]).any { a -> a.trim() == key } }
            .map { key -> diagnostic(operation.pointer, operation.method + " " + path.path,
                "Uses the non-standard extension '" + key + "'") } } }
        + api.schemas.expand { schema -> schema.extensionKeys
            .filter { key -> !tokenize(",", "" + rule.parameters["allowed"]).any { a -> a.trim() == key } }
            .map { key -> diagnostic(schema.pointer, schema.name,
                "Uses the non-standard extension '" + key + "'") } }
        + api.schemas.expand { schema -> schema.properties.expand { prop -> prop.extensionKeys
            .filter { key -> !tokenize(",", "" + rule.parameters["allowed"]).any { a -> a.trim() == key } }
            .map { key -> diagnostic(prop.pointer, schema.name + "." + prop.name,
                "Uses the non-standard extension '" + key + "'") } } }
        + api.paths.expand { path -> path.operationDetails.expand { operation -> operation.parameters.expand { prm -> prm.extensionKeys
            .filter { key -> !tokenize(",", "" + rule.parameters["allowed"]).any { a -> a.trim() == key } }
            .map { key -> diagnostic(prm.pointer, prm.name,
                "Uses the non-standard extension '" + key + "'") } } } };
}
