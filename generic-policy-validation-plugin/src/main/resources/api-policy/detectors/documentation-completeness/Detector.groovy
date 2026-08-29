{ Map api, Map rule ->
    def require = rule.parameters.require
    def scope = rule.scope
    def out = []
    def blank = { v -> v == null || (v instanceof String && v.trim().isEmpty()) }
    def check = { item, pointer, where ->
        def missingDescription = require != 'example' && blank(item.description)
        def missingExample = require != 'description' && !item.examplePresent
        if (missingDescription && missingExample) out << [pointer: pointer, path: where, message: "${where} has no description or example"]
        else if (missingDescription) out << [pointer: pointer, path: where, message: "${where} has no description"]
        else if (missingExample) out << [pointer: pointer, path: where, message: "${where} has no example"]
    }
    if (scope == 'property') {
        (api.schemas ?: []).each { schema ->
            (schema.properties ?: []).each { prop -> check(prop, prop.pointer, "${schema.name}.${prop.name}") }
        }
    } else if (scope == 'parameter') {
        (api.paths ?: []).each { path ->
            (path.operationDetails ?: []).each { op ->
                (op.parameters ?: []).each { prm -> check(prm, prm.pointer, "${op.method} ${path.path} ${prm.name}") }
            }
        }
    }
    out
}
