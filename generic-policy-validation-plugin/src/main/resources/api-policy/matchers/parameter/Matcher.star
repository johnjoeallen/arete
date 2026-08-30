def detect(api, rule):
    p = rule["parameters"]
    check = p["check"]
    maximum = p.get("maximum")
    out = []
    for path in api["paths"]:
        template = path["templateParameters"]
        for op in path["operationDetails"]:
            params = op["parameters"]
            loc = op["method"] + " " + path["path"]
            if check == "max-count":
                if maximum != None and len(params) > maximum:
                    out.append({
                        "pointer": op["pointer"],
                        "path": loc,
                        "message": "Operation declares " + str(len(params)) + " parameters, more than the maximum of " + str(maximum),
                    })
            elif check == "path-required":
                for prm in params:
                    if prm["in"] == "path" and not prm["required"]:
                        out.append({
                            "pointer": prm["pointer"],
                            "path": loc + " " + prm["name"],
                            "message": "Path parameter '" + prm["name"] + "' is not marked required",
                        })
            elif check == "template-match":
                declared = [prm["name"] for prm in params if prm["in"] == "path"]
                for name in declared:
                    if name not in template:
                        out.append({
                            "pointer": op["pointer"],
                            "path": loc,
                            "message": "Path parameter '" + name + "' has no matching {placeholder} in the path template",
                        })
                for name in template:
                    if name not in declared:
                        out.append({
                            "pointer": op["pointer"],
                            "path": loc,
                            "message": "Path template placeholder '{" + name + "}' has no matching path parameter",
                        })
            elif check == "schema-present":
                for prm in params:
                    if not prm["schemaPresent"]:
                        out.append({
                            "pointer": prm["pointer"],
                            "path": loc + " " + prm["name"],
                            "message": "Parameter '" + prm["name"] + "' defines neither a schema nor content",
                        })
    return out
