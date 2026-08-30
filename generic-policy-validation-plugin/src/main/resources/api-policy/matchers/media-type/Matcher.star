# Port of Matcher.groovy (issue #125).

def _wildcard(media_type):
    return media_type == "*/*" or media_type.endswith("/*") or ("*" in media_type)

def _matches(types, match, allowed):
    if match == "absent":
        return len(types) == 0
    if match == "wildcard":
        return any([_wildcard(str(t)) for t in types])
    if match == "not-allowed":
        return any([str(t).lower() not in allowed for t in types])
    return False

def detect(api, rule):
    p = rule["parameters"]
    raw = p.get("allowed") or ""
    allowed = [token.strip().lower() for token in str(raw).split(",") if token.strip()]
    match = p.get("match")

    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            if p.get("location") == "request":
                if _matches(op["requestMediaTypes"], match, allowed):
                    out.append({"pointer": op["pointer"], "path": op["method"] + " " + path["path"],
                                "message": "Request body media type " + p["match"]})
                continue
            for resp in op["responses"]:
                if _matches(resp["mediaTypes"], match, allowed):
                    out.append({"pointer": op["pointer"], "path": op["method"] + " " + path["path"],
                                "message": "Response " + resp["status"] + " media type " + p["match"]})
    return out
