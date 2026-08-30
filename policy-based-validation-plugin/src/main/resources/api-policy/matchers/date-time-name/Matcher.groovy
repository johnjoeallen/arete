{ Map api, Map rule ->
    (api.schemas ?: []).collectMany { it.properties ?: [] }.findAll { p -> p.type == 'string' && p.format == 'date-time' && !p.name.endsWith(rule.parameters.suffix) }.collect { p -> [pointer: p.pointer, path: p.name, message: 'Date-time property name does not end with ' + rule.parameters.suffix] }
}
