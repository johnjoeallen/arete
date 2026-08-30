distill(api, rule) {
    return api.schemas.expand { schema -> schema.properties
        .filter { prop -> prop.name =~ ("(?i)" + rule.parameters["name-pattern"])
            && ((rule.parameters["check"] == "string" && prop.type != "string")
                || (rule.parameters["check"] == "format" && prop.format != rule.parameters["format"])) }
        .map { prop -> diagnostic(prop.pointer, prop.name,
            rule.parameters["check"] == "string"
                ? "Identifier property should use type string: " + prop.name
                : "Identifier property should declare format " + rule.parameters["format"] + ": " + prop.name) } };
}
