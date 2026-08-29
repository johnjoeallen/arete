/* Checks effective operation security requirements for a configured scheme. */
{ Map api, Map rule ->
    def expected = rule.parameters?.scheme?.toString()
    def expectedScopes = (rule.parameters?.scopes ?: '').split(',').collect { it.trim() }.findAll { it }
    def globalSecurity = api.security
    api.paths.collectMany { path ->
        path.operationDetails.findAll { operation ->
            def security = operation.security == null ? globalSecurity : operation.security
            !(security instanceof List) || security.isEmpty() || !security.any { requirement ->
                if (!(requirement instanceof Map) || !requirement.containsKey(expected)) return false
                expectedScopes.isEmpty() || (requirement[expected] instanceof List && expectedScopes.every { requiredScope -> requirement[expected].collect { it.toString() }.contains(requiredScope) })
            }
        }.collect { operation ->
            [pointer: operation.pointer, path: operation.method + ' ' + path.path,
             message: expectedScopes.isEmpty()
                     ? 'Operation does not require security scheme ' + expected
                     : 'Operation does not require security scheme ' + expected + ' with scopes ' + expectedScopes.join(', ')]
        }
    }
}
