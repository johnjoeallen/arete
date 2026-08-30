/* Checks the declared OpenAPI document version against policy support. */
{ Map api, Map rule ->
    def allowed = (rule.parameters?.allowed ?: '').toString().split(',').collect { it.trim() }.findAll { it }
    def version = api.info?.openapiVersion?.toString()
    def supported = version != null && allowed.any { version == it || version.startsWith(it + '.') }
    supported ? [] : [[pointer: '/info', path: 'API', message: 'Document declares unsupported or missing OpenAPI version: ' + (version ?: 'none')]]
}
