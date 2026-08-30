{ Map api, Map rule ->
    def allowed = ((rule.parameters.allowed ?: '') as String).split(',').collect { it.trim() }.findAll { it }
    def out = []
    def report = { keys, pointer, where ->
        (keys ?: []).findAll { !allowed.contains(it) }.each { key ->
            out << [pointer: pointer, path: where, message: "Uses the non-standard extension '${key}'"]
        }
    }
    report(api.info?.extensionKeys, '/info', 'info')
    (api.paths ?: []).each { path ->
        (path.operationDetails ?: []).each { op ->
            report(op.extensionKeys, op.pointer, "${op.method} ${path.path}")
            (op.parameters ?: []).each { prm -> report(prm.extensionKeys, prm.pointer, prm.name) }
        }
    }
    (api.schemas ?: []).each { schema ->
        report(schema.extensionKeys, schema.pointer, schema.name)
        (schema.properties ?: []).each { prop -> report(prop.extensionKeys, prop.pointer, "${schema.name}.${prop.name}") }
    }
    out
}
