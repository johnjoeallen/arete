distill(api, rule) {
    return api.servers
        .filter { url -> !(urlHost(url) != null
            && urlHost(url) ==~ /[a-z0-9]+(?:-[a-z0-9]+)*/) }
        .map { url -> diagnostic("/servers", url,
            "Server hostname is not lowercase hyphenated") };
}
