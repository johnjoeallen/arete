def _status(value):
    return parse_int(str(value), -1)

def _class_matches(code, wanted):
    if wanted == "success":
        return code >= 200 and code < 300
    if wanted == "client-error":
        return code >= 400 and code < 500
    if wanted == "server-error":
        return code >= 500 and code < 600
    return False

def _has_header(headers, expected):
    expected = expected.lower()
    for header in headers:
        if str(header).lower() == expected:
            return True
    return False

def detect(api, rule):
    p = rule["parameters"]
    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            location = op["method"] + " " + path["path"]
            if rule["scope"] == "operation":
                wanted = p.get("required-class")
                if wanted and not any([_class_matches(_status(resp["status"]), wanted) for resp in op["responses"]]):
                    out.append({"pointer": op["pointer"], "path": location,
                                "message": "Operation does not document a " + wanted + " response"})
                continue
            for resp in op["responses"]:
                code = _status(resp["status"])
                if p.get("status") != None and code != p["status"]:
                    continue
                if p.get("require-description") and code >= 400 and code < 600 and not resp["description"]:
                    out.append({"pointer": op["pointer"], "path": location,
                                "message": "Error response is missing a description"})
                elif p.get("problem-json") and code >= 400 and code < 600 and "application/problem+json" not in resp["mediaTypes"]:
                    out.append({"pointer": op["pointer"], "path": location,
                                "message": "Error response does not declare application/problem+json"})
                elif p.get("required-header") and not _has_header(resp["headers"], p["required-header"]):
                    out.append({"pointer": op["pointer"], "path": location,
                                "message": "Response " + str(resp["status"]) + " is missing " + p["required-header"] + ""})
    return out
