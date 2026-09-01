{ Map api, Map rule ->
    (api.schemas ?: []).collectMany { it.properties ?: [] }.findAll { p ->
        (p.name == 'id' && p.type != 'string') ||
        ((p.name == 'created' || p.name == 'modified') && !(p.type == 'string' && p.format == 'date-time'))
    }.collect { p -> [pointer: p.pointer, path: p.name, message: 'Common field has an inconsistent type or format'] }
}
