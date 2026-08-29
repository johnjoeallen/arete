# Port of Detector.groovy (issue #125).

def _trimmed_summary(op):
    summary = op["summary"]
    return summary.strip() if summary != None else ""

def _matches(op, p):
    if p.get("method") and op["method"] != p["method"]:
        return False
    trimmed = _trimmed_summary(op)
    if p.get("summary") == "present" and not trimmed:
        return False
    if p.get("summary") == "absent" and trimmed:
        return False
    description = op["description"].strip() if op.get("description") != None else ""
    if p.get("description") == "present" and not description:
        return False
    if p.get("description") == "absent" and description:
        return False
    if p.get("request-body") == "present" and not op["requestBodyPresent"]:
        return False
    if p.get("request-body") == "absent" and op["requestBodyPresent"]:
        return False
    return True

def _message(p):
    if p.get("summary") == "absent":
        return "Operation summary is missing"
    if p.get("description") == "absent":
        return "Operation description is missing"
    if p.get("request-body") == "present":
        return "Operation has a request body"
    if p.get("request-body") == "absent":
        return "Operation has no request body"
    if p.get("method"):
        return p["method"] + " operation is used"
    return "Operation matches the configured rule"

def detect(api, rule):
    p = rule["parameters"]
    message = _message(p)
    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            if _matches(op, p):
                out.append({
                    "pointer": op["pointer"],
                    "path": op["method"] + " " + path["path"],
                    "message": message,
                })
    return out
