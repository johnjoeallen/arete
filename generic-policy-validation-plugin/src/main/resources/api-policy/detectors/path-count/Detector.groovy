{ Map api, Map rule ->
    if (rule.parameters.containsKey('maximum-depth')) return (api.paths ?: []).findAll { it.path.tokenize('/').count { !it.startsWith('{') } > rule.parameters['maximum-depth'] }.collect { [pointer: it.pointer, path: it.path, message: 'Resource path exceeds the maximum nesting depth'] }
    if (rule.parameters['nested-root']) {
        def paths = (api.paths ?: [])
        def roots = paths.collect { it.path.tokenize('/').find { !it.startsWith('{') } }.findAll().toSet()
        return paths.findAll { path -> def parts = path.path.tokenize('/').findAll { !it.startsWith('{') }; parts.size() > 1 && !roots.contains(parts.last()) }.collect { [pointer: it.pointer, path: it.path, message: 'Nested resource type is not exposed as a root resource'] }
    }
    def resources = (api.paths ?: []).collect { it.path.tokenize('/').find { !it.startsWith('{') } }.findAll().toSet()
    resources.size() > rule.parameters.maximum ? [[pointer: '/paths', path: 'API', message: 'API has ' + resources.size() + ' top-level resource types (maximum ' + rule.parameters.maximum + ')']] : []
}
