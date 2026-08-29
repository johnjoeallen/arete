{ Map api, Map rule ->
    def info = api.info ?: [:]
    def missing = []
    ['title': 'title', 'description': 'description', 'contactName': 'contact name', 'contactEmail': 'contact email'].each { key, label ->
        if (!(info[key] instanceof String) || info[key].trim().isEmpty()) missing << label
    }
    def version = info.version
    if (!(version instanceof String) || !(version ==~ /0|[1-9][0-9]*\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?/)) missing << 'semantic version'
    missing.collect { field -> [pointer: '/info', path: 'API', message: 'API metadata is missing ' + field] }
}
