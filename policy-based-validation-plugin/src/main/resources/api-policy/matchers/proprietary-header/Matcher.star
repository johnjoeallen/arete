# Port of Matcher.groovy (issue #125).
_STANDARD = ["accept", "accept-charset", "accept-encoding", "accept-language",
             "authorization", "cache-control", "content-length", "content-type",
             "cookie", "date", "etag", "expect", "from", "host", "if-match",
             "if-modified-since", "if-none-match", "if-range", "if-unmodified-since",
             "origin", "pragma", "referer", "user-agent", "warning", "www-authenticate",
             "location", "retry-after", "server", "set-cookie", "vary"]

def _proprietary(name):
    lower = str(name).lower()
    return lower not in _STANDARD and (lower.startswith("x-") or lower.startswith("x_"))

def detect(api, rule):
    raw = rule["parameters"].get("allowed") or ""
    allowed = [token.strip().lower() for token in str(raw).split(",") if token.strip()]

    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            for param in op["parameters"]:
                if (param["in"] == "header" and _proprietary(param["name"])
                        and str(param["name"]).lower() not in allowed):
                    out.append({
                        "pointer": param["pointer"],
                        "path": op["method"] + " " + path["path"],
                        "message": "Proprietary request header is not allow-listed: " + param["name"],
                    })
            for resp in op["responses"]:
                for header in resp["headers"]:
                    if _proprietary(header) and str(header).lower() not in allowed:
                        out.append({
                            "pointer": op["pointer"],
                            "path": op["method"] + " " + path["path"],
                            "message": "Proprietary response header is not allow-listed: " + header,
                        })
    return out
