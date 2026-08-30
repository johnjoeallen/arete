# Port of Matcher.groovy (issue #125).

def _present_ci(headers, expected):
    lowered = expected.lower()
    for header in headers:
        if str(header).lower() == lowered:
            return True
    return False

def detect(api, rule):
    p = rule["parameters"]
    if p.get("headers"):
        expected_headers = [token.strip() for token in str(p["headers"]).split(",") if token.strip()]
    else:
        expected_headers = [str(p["header"])]
    status_str = str(p["status"])
    required = p.get("required")

    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            for resp in op["responses"]:
                if str(resp["status"]) != status_str:
                    continue
                if required:
                    hit = not all([_present_ci(resp["headers"], e) for e in expected_headers])
                else:
                    hit = any([_present_ci(resp["headers"], e) for e in expected_headers])
                if hit:
                    qualifier = "lacks one or more required" if required else "contains an unexpected"
                    out.append({
                        "pointer": op["pointer"],
                        "path": op["method"] + " " + path["path"],
                        "message": "Response " + str(resp["status"]) + " " + qualifier + " headers: " + ", ".join(expected_headers),
                    })
    return out
