_INTERNAL_SUFFIXES = [".internal", ".local", ".corp", ".intranet", ".lan", ".home", ".test"]
_PRIVATE_HOST = r"(127\..*|10\..*|192\.168\..*|172\.(1[6-9]|2[0-9]|3[01])\..*)"


def _is_internal(host):
    h = host.lower()
    if h == "localhost" or re_fullmatch(_PRIVATE_HOST, h):
        return True
    for suffix in _INTERNAL_SUFFIXES:
        if h.endswith(suffix):
            return True
    if "." not in h and h != "":
        return True
    return False


def detect(api, rule):
    check = rule["parameters"]["check"]
    out = []
    if check != "internal-host":
        return out
    for url in api["servers"]:
        host = url_host(url)
        if host and _is_internal(host):
            out.append({
                "pointer": "/servers",
                "path": url,
                "message": "Server URL points at an internal or non-routable host: " + host,
            })
    return out
