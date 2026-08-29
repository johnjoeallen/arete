{ Map api, Map rule ->
    def normalize = { String path ->
        path.split('/', -1).collect { seg -> (seg.startsWith('{') && seg.endsWith('}')) ? '{}' : seg }.join('/')
    }
    def out = []
    def seen = [:]
    (api.paths ?: []).each { path ->
        def norm = normalize(path.path as String)
        if (seen.containsKey(norm)) {
            out << [pointer: path.pointer, path: path.path, message: "Path is structurally identical to ${seen[norm]}"]
        } else {
            seen[norm] = path.path
        }
    }
    out
}
