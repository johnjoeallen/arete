distill(api, rule) {
    return api.schemas.expand { schema -> schema.properties
        .filter { prop ->
            (rule.parameters["type"] != null && prop.type != rule.parameters["type"]) ? false
            : ((rule.parameters["format"] == "absent")
                    && (!(["integer", "number"].any { t -> t == prop.type }) || truthy(prop.format))) ? false
            : ((rule.parameters["format"] == "present") && !truthy(prop.format)) ? false
            : (rule.parameters["enum-type"] == "consistent")
                ? (truthy(prop.enumPresent) && prop.enumValues.any { v ->
                        prop.type == "string" ? type(v) != "string"
                        : prop.type == "integer" ? type(v) != "int"
                        : prop.type == "number" ? !(["int", "float"].any { x -> x == type(v) })
                        : false })
            : (rule.parameters["extensible"] == "required")
                ? (truthy(prop.enumPresent) && !truthy(prop.extensibleEnum))
            : (rule.parameters["enum-case"] == "upper-snake-case")
                ? (truthy(prop.enumPresent) && prop.enumValues.any { v ->
                        type(v) == "string" && !(("" + v) ==~ /[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*/) })
            : ((rule.parameters["max-items"] == "absent") && prop.maxItems != null) ? false
            : ((rule.parameters["max-items"] == "present") && prop.maxItems == null) ? false
            : ((rule.parameters["enum"] == "present") && !truthy(prop.enumPresent)) ? false
            : ((rule.parameters["enum"] == "absent") && truthy(prop.enumPresent)) ? false
            : (rule.parameters.keys.any { k -> k == "nullable" } && prop.nullable != rule.parameters["nullable"]) ? false
            : (rule.parameters.keys.any { k -> k == "required" } && prop.required != rule.parameters["required"]) ? false
            : true }
        .map { prop -> diagnostic(prop.pointer, prop.name,
            rule.parameters["enum"] == "present" ? "Property uses an enum"
            : rule.parameters["enum"] == "absent" ? "Property does not use an enum"
            : (rule.parameters.keys.any { k -> k == "nullable" } && rule.parameters["required"] == false)
                ? "Optional property explicitly permits null"
            : rule.parameters["max-items"] == "absent" ? "Array property has no maximum item count"
            : rule.parameters["format"] == "absent" ? "Numeric property does not declare a format"
            : "Property matches the configured schema rule") } };
}
