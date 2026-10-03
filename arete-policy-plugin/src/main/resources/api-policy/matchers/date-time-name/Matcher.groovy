{ Map api, Map rule ->
    (api.schemas ?: []).collectMany { it.properties ?: [] }.findAll { p -> p.type == 'string' && p.format == 'date-time' && !rule.parameters.suffix.toString().split(',').any { s -> p.name.endsWith(s.trim()) } }.collect { p -> [pointer: p.pointer, path: p.name, message: 'Date-time property name does not end with one of: ' + rule.parameters.suffix] }
}
