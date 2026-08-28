/* Versioning facts are derived only from the stable detector map. */
{ Map api, Map rule ->
    def p = rule.parameters ?: [:]
    def versionUri = { path -> path.path ==~ /.*\/(v[0-9]+|version[0-9]+)(\/.*)?/ }
    def versionHeader = { op -> (op.parameters ?: []).any { it.in == 'header' && it.name ==~ /(?i)(api[-_])?version|x-api-version/ } }
    def versionMedia = { op -> (op.mediaTypes ?: []).any { it ==~ /(?i).*\+?v[0-9]+.*|.*version[0-9]+.*/ } }
    def found = { path ->
        if (p.location == 'uri') return versionUri(path)
        if (p.location == 'header') return path.operationDetails.any(versionHeader)
        if (p.location == 'media-type') return path.operationDetails.any(versionMedia)
        false
    }
    def anyVersion = { path -> versionUri(path) || path.operationDetails.any { op -> versionHeader(op) || versionMedia(op) } }
    if (p.match == 'absent') return api.paths.any(anyVersion) ? [] : [[pointer:'/paths', path:'API', message:'Interface has no explicit versioning']]
    api.paths.findAll(found).collect { path -> [pointer:path.pointer, path:path.path, message:'Interface version is exposed through ' + p.location] }
}
