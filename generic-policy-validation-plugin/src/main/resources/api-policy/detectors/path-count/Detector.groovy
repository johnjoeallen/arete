{ Map api, Map rule ->
    if (rule.parameters.containsKey('maximum-depth')) return (api.paths ?: []).findAll { it.path.tokenize('/').count { !it.startsWith('{') } > rule.parameters['maximum-depth'] }.collect { [pointer: it.pointer, path: it.path, message: 'Resource path exceeds the maximum nesting depth'] }
    def resources = (api.paths ?: []).collect { it.path.tokenize('/').find { !it.startsWith('{') } }.findAll().toSet()
    resources.size() > rule.parameters.maximum ? [[pointer: '/paths', path: 'API', message: 'API has ' + resources.size() + ' top-level resource types (maximum ' + rule.parameters.maximum + ')']] : []
}
