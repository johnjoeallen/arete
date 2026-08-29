# Port of Detector.groovy (issue #125). The semantic-conflict regex keeps the
# original's doubled backslashes verbatim so results match the Groovy detector.
_CONFLICT = r"(?i).*\\b(error|failure|failed|invalid)\\b.*"

def _status(value):
    return parse_int(str(value), -1)

def _operation_matches(path, op, p):
    operation_type = p.get("operation-type")
    if operation_type == "create" and not (op["method"] == "POST" or op["method"] == "PUT"):
        return False
    if operation_type == "identifiable-resource-retrieval" and not (op["method"] == "GET" and "{" in path["path"]):
        return False
    required_status = p.get("required-status")
    if required_status != None and not any([_status(resp["status"]) == required_status for resp in op["responses"]]):
        return True
    return False

def _response_matches(resp, p):
    if p.get("status") != None and _status(resp["status"]) != p["status"]:
        return False
    if p.get("match") == "semantic-conflict":
        code = _status(resp["status"])
        return code >= 200 and code < 300 and re_fullmatch(_CONFLICT, resp["description"] or "")
    return p.get("status") != None

def detect(api, rule):
    p = rule["parameters"]

    if p.get("response-shape") == "json-object":
        out = []
        for path in api["paths"]:
            for op in path["operationDetails"]:
                for resp in op["responses"]:
                    code = _status(resp["status"])
                    if code >= 200 and code < 300 and resp["schemaTypes"] and any([t != "object" for t in resp["schemaTypes"]]):
                        out.append({"pointer": path["pointer"], "path": op["method"] + " " + path["path"],
                                    "message": "Successful response is not a JSON object"})
        return out

    if p.get("error-format") == "problem-json":
        out = []
        for path in api["paths"]:
            for op in path["operationDetails"]:
                for resp in op["responses"]:
                    code = _status(resp["status"])
                    if code >= 400 and code < 600 and "application/problem+json" not in op["mediaTypes"]:
                        out.append({"pointer": path["pointer"], "path": op["method"] + " " + path["path"],
                                    "message": "Error response does not declare application/problem+json"})
        return out

    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            if rule["scope"] == "operation":
                if _operation_matches(path, op, p):
                    out.append({"pointer": op["pointer"], "path": op["method"] + " " + path["path"],
                                "message": "Operation lacks the required documented status"})
                continue
            for resp in op["responses"]:
                if _response_matches(resp, p):
                    if p.get("match") == "semantic-conflict":
                        message = "Status code conflicts with response semantics"
                    else:
                        message = "Response uses the configured status code"
                    out.append({"pointer": op["pointer"], "path": op["method"] + " " + path["path"], "message": message})
    return out
