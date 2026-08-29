{ Map api, Map rule ->
    def suffixes = ['.internal', '.local', '.corp', '.intranet', '.lan', '.home', '.test']
    def privateHost = ~/(127\..*|10\..*|192\.168\..*|172\.(1[6-9]|2[0-9]|3[01])\..*)/
    def isInternal = { String host ->
        def h = host.toLowerCase()
        if (h == 'localhost' || h ==~ privateHost) return true
        if (suffixes.any { h.endsWith(it) }) return true
        if (!h.contains('.') && !h.isEmpty()) return true
        false
    }
    def out = []
    if (rule.parameters.check != 'internal-host') return out
    (api.servers ?: []).each { url ->
        def host = null
        try { host = new URI(url as String).host } catch (ignored) { }
        if (host && isInternal(host)) {
            out << [pointer: '/servers', path: url,
                    message: "Server URL points at an internal or non-routable host: ${host}"]
        }
    }
    out
}
