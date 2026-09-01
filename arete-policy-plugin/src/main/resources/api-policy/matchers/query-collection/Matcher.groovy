/* Validates the configured serialization of array-valued query parameters. */
{ Map api, Map rule ->
    def expectedStyle = rule.parameters?.style?.toString()
    def expectedExplode = rule.parameters?.explode as Boolean
    def effectiveStyle = { parameter -> (parameter.style ?: 'form').toString() }
    def effectiveExplode = { parameter -> parameter.explode == null ? effectiveStyle(parameter) == 'form' : parameter.explode as Boolean }
    api.paths.collectMany { path ->
        path.operationDetails.collectMany { operation ->
            (operation.parameters ?: []).findAll { parameter ->
                parameter.in == 'query' && parameter.schemaType == 'array' &&
                    (effectiveStyle(parameter) != expectedStyle || effectiveExplode(parameter).booleanValue() != expectedExplode.booleanValue())
            }.collect { parameter ->
                [pointer: parameter.pointer, path: operation.method + ' ' + path.path,
                 message: 'Collection query parameter ' + parameter.name + ' does not use ' + expectedStyle + ' serialization with explode=' + expectedExplode]
            }
        }
    }
}
