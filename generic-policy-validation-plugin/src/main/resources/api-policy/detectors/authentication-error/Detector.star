def _status(value):
    return parse_int(str(value), -1)

def _has_header(headers, expected):
    expected = expected.lower()
    for header in headers:
        if str(header).lower() == expected:
            return True
    return False

def _secured(api, op):
    requirements = op["security"] if op["security"] != None else api["security"]
    return requirements != None and len(requirements) > 0

def detect(api, rule):
    p = rule["parameters"]
    out = []
    reported = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            location = op["method"] + " " + path["path"]
            if rule["scope"] == "operation":
                if not _secured(api, op):
                    continue
                required = p.get("required-status")
                if required != None and not any([_status(resp["status"]) == required for resp in op["responses"]]):
                    out.append({"pointer": op["pointer"], "path": location,
                                "message": "Secured operation does not document response " + str(required)})
                continue
            for resp in op["responses"]:
                code = _status(resp["status"])
                if p.get("required-status") != None and code != p["required-status"]:
                    continue
                if p.get("required-header") and not _has_header(resp["headers"], p["required-header"]):
                    key = op["pointer"] + ":" + str(resp["status"]) + ":missing:" + p["required-header"]
                    if key not in reported:
                        reported.append(key)
                        out.append({"pointer": op["pointer"], "path": location,
                                    "message": "Authentication response is missing " + p["required-header"]})
                if p.get("forbidden-header") and _has_header(resp["headers"], p["forbidden-header"]):
                    key = op["pointer"] + ":" + str(resp["status"]) + ":forbidden:" + p["forbidden-header"]
                    if key not in reported:
                        reported.append(key)
                        out.append({"pointer": op["pointer"], "path": location,
                                    "message": "Authorization response must not include " + p["forbidden-header"]})
    return out
