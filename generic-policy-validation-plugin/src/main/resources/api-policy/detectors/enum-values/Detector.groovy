{ Map api, Map rule ->
    def check = rule.parameters.check
    def out = []
    (api.schemas ?: []).each { schema ->
        (schema.properties ?: []).findAll { it.enumPresent }.each { prop ->
            if (check == 'no-duplicates') {
                def keys = (prop.enumValues ?: []).collect { it as String }
                if (keys.size() != keys.unique(false).size()) {
                    out << [pointer: prop.pointer, path: prop.name,
                            message: "Enum for '${prop.name}' contains duplicate values"]
                }
            }
        }
    }
    out
}
