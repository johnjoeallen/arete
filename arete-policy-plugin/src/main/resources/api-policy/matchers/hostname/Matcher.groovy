{ Map api, Map rule ->
    (api.servers ?: []).collectMany { url ->
        def host = new URI(url).host
        (host && host ==~ /[a-z0-9]+(?:-[a-z0-9]+)*/) ? [] : [[pointer: '/servers', path: url, message: 'Server hostname is not lowercase hyphenated']]
    }
}
