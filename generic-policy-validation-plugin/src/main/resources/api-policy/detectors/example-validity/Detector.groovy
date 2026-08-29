{ Map api, Map rule ->
    def check = rule.parameters.check
    def out = []
    def number = { v -> (v instanceof Number) ? v.doubleValue() : null }

    if (check == 'covers-required') {
        (api.schemas ?: []).each { schema ->
            def example = schema.example
            if (example instanceof Map) {
                (schema.requiredFields ?: []).findAll { !example.containsKey(it) }.each { field ->
                    out << [pointer: schema.pointer, path: schema.name,
                            message: "Schema example omits the required field '${field}'"]
                }
            }
        }
    } else if (check == 'satisfies-constraints') {
        (api.schemas ?: []).each { schema ->
            (schema.properties ?: []).findAll { it.examplePresent }.each { prop ->
                def example = prop.example
                def loc = "${schema.name}.${prop.name}"
                if (prop.pattern && example instanceof String && !(example =~ (prop.pattern as String))) {
                    out << [pointer: prop.pointer, path: loc, message: "Example does not match pattern ${prop.pattern}"]
                }
                if (example instanceof String) {
                    if (prop.minLength != null && example.length() < prop.minLength) out << [pointer: prop.pointer, path: loc, message: "Example is shorter than minLength ${prop.minLength}"]
                    if (prop.maxLength != null && example.length() > prop.maxLength) out << [pointer: prop.pointer, path: loc, message: "Example is longer than maxLength ${prop.maxLength}"]
                }
                def n = number(example)
                if (n != null) {
                    def min = number(prop.minimum); def max = number(prop.maximum)
                    if (min != null) {
                        if (prop.exclusiveMinimum && n <= min) out << [pointer: prop.pointer, path: loc, message: "Example is not greater than exclusive minimum ${min}"]
                        else if (!prop.exclusiveMinimum && n < min) out << [pointer: prop.pointer, path: loc, message: "Example is below minimum ${min}"]
                    }
                    if (max != null) {
                        if (prop.exclusiveMaximum && n >= max) out << [pointer: prop.pointer, path: loc, message: "Example is not less than exclusive maximum ${max}"]
                        else if (!prop.exclusiveMaximum && n > max) out << [pointer: prop.pointer, path: loc, message: "Example exceeds maximum ${max}"]
                    }
                }
                if (prop.enumPresent && (example instanceof String || example instanceof Number || example instanceof Boolean)) {
                    def allowed = (prop.enumValues ?: []).collect { it as String }
                    if (!allowed.contains(example as String)) out << [pointer: prop.pointer, path: loc, message: 'Example is not one of the declared enum values']
                }
            }
        }
    }
    out
}
