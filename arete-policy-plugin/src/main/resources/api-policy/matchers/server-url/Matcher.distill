distill(api, rule) {
    return rule.parameters["check"] == "internal-host"
        ? api.servers
            .filter { url -> urlHost(url) != null
                && (urlHost(url).lower() == "localhost"
                    || urlHost(url).lower() ==~ /(127\..*|10\..*|192\.168\..*|172\.(1[6-9]|2[0-9]|3[01])\..*)/
                    || urlHost(url).lower() ==~ /.*(\.internal|\.local|\.corp|\.intranet|\.lan|\.home|\.test)/
                    || (!urlHost(url).contains(".") && urlHost(url) != "")) }
            .map { url -> occurrence("/servers", url,
                "Server URL points at an internal or non-routable host: " + urlHost(url)) }
        : rule.parameters["check"] == "placeholder-host"
        ? api.servers
            .filter { url -> urlHost(url) != null
                && urlHost(url).lower() ==~ /(.*\.)?example\.(com|org|net)/ }
            .map { url -> occurrence("/servers", url,
                "Server URL uses the documentation placeholder host " + urlHost(url)) }
        : rule.parameters["check"] == "trailing-slash"
        ? api.servers
            .filter { url -> url.endsWith("/") && url != "/" }
            .map { url -> occurrence("/servers", url,
                "Server URL has a trailing slash, which produces '//' when joined with a path") }
        : (rule.parameters["check"] == "url-pattern" && rule.parameters["pattern"] != null)
            ? api.servers
                .filter { url -> !(url ==~ rule.parameters["pattern"]) }
                .map { url -> occurrence("/servers", url,
                    "Server URL does not match the approved pattern " + rule.parameters["pattern"]) }
            : api.servers.filter { url -> false };
}
