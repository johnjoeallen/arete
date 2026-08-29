# Port of Detector.groovy (issue #125).

def _bool_text(value):
    return "true" if value else "false"

def _effective_style(param):
    return str(param["style"]) if param["style"] != None else "form"

def _effective_explode(param):
    if param["explode"] == None:
        return _effective_style(param) == "form"
    return param["explode"]

def detect(api, rule):
    p = rule["parameters"]
    expected_style = p.get("style")
    expected_explode = p.get("explode")

    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            for param in op["parameters"]:
                if param["in"] != "query" or param["schemaType"] != "array":
                    continue
                if _effective_style(param) != expected_style or _effective_explode(param) != expected_explode:
                    out.append({
                        "pointer": param["pointer"],
                        "path": op["method"] + " " + path["path"],
                        "message": ("Collection query parameter " + param["name"]
                                    + " does not use " + expected_style
                                    + " serialization with explode=" + _bool_text(expected_explode)),
                    })
    return out
