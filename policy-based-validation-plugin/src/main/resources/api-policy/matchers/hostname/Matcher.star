# Port of Matcher.groovy (issue #125). url_host replaces new URI(url).host.
def detect(api, rule):
    out = []
    for url in api["servers"]:
        host = url_host(url)
        if host and re_fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", host):
            continue
        out.append({
            "pointer": "/servers",
            "path": url,
            "message": "Server hostname is not lowercase hyphenated",
        })
    return out
