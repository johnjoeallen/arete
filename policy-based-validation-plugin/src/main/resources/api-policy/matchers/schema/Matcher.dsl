distill(api, rule) {
    return checks(api.schemaProperties
            .filter { p -> rule.parameters["type"] == null || p.type == rule.parameters["type"] }) {

        filter { p -> rule.parameters["format"] == "absent"
                      && ["integer", "number"].any { t -> t == p.type } && !truthy(p.format) }
          .map { p -> occurrence(p.pointer, p.name,
                                 "Numeric property does not declare a format") },

        filter { p -> rule.parameters["max-length"] == "absent"
                      && p.type == "string" && p.maxLength == null }
          .map { p -> occurrence(p.pointer, p.name,
                                 "String property does not declare a maximum length") },

        filter { p -> rule.parameters["max-items"] == "absent" && p.maxItems == null }
          .map { p -> occurrence(p.pointer, p.name,
                                 "Array property has no maximum item count") },

        filter { p -> rule.parameters["bounds"] == "complete"
                      && ["integer", "number"].any { t -> t == p.type }
                      && !(p.minimum != null && p.maximum != null) }
          .map { p -> occurrence(p.pointer, p.name,
                                 "Numeric property does not declare both a minimum and a maximum") },

        filter { p -> rule.parameters["enum-type"] == "consistent"
                      && truthy(p.enumPresent)
                      && p.enumValues.any { v ->
                             p.type == "string" ? type(v) != "string"
                             : p.type == "integer" ? type(v) != "int"
                             : p.type == "number" ? !(["int", "float"].any { x -> x == type(v) })
                             : false } }
          .map { p -> occurrence(p.pointer, p.name,
                                 "Enum value type is inconsistent with the property type") },

        filter { p -> rule.parameters["enum-case"] == "upper-snake-case"
                      && truthy(p.enumPresent)
                      && p.enumValues.any { v ->
                             type(v) == "string" && !(("" + v) ==~ /[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*/) } }
          .map { p -> occurrence(p.pointer, p.name,
                                 "Enum value is not UPPER_SNAKE_CASE") },

        filter { p -> rule.parameters["extensible"] == "required"
                      && truthy(p.enumPresent) && !truthy(p.extensibleEnum) }
          .map { p -> occurrence(p.pointer, p.name,
                                 "Enum is not marked extensible") },

        filter { p -> rule.parameters["enum"] == "present" && truthy(p.enumPresent) }
          .map { p -> occurrence(p.pointer, p.name, "Property uses an enum") },

        filter { p -> rule.parameters["enum"] == "absent" && !truthy(p.enumPresent) }
          .map { p -> occurrence(p.pointer, p.name, "Property does not use an enum") },

        filter { p -> (rule.parameters.keys.any { k -> k == "nullable" }
                       || rule.parameters.keys.any { k -> k == "required" })
                      && (!(rule.parameters.keys.any { k -> k == "nullable" })
                          || p.nullable == rule.parameters["nullable"])
                      && (!(rule.parameters.keys.any { k -> k == "required" })
                          || p.required == rule.parameters["required"]) }
          .map { p -> occurrence(p.pointer, p.name,
                     (rule.parameters["nullable"] == true && rule.parameters["required"] == false)
                         ? "Optional property explicitly permits null"
                         : "Property matches the flagged nullable/required condition") }
    };
}
