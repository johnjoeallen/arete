def detect(api, rule):
    check = rule["parameters"]["check"]
    out = []
    seen = {}
    for path in api["paths"]:
        for op in path["operationDetails"]:
            loc = op["method"] + " " + path["path"]
            if check == "unique-operation-id":
                oid = op["operationId"]
                if oid == None or (type(oid) == "string" and oid.strip() == ""):
                    out.append({"pointer": op["pointer"], "path": loc, "message": "Operation has no operationId"})
                elif oid in seen:
                    out.append({"pointer": op["pointer"], "path": loc, "message": "operationId '" + oid + "' is also used by " + seen[oid]})
                else:
                    seen[oid] = loc
            elif check == "tags-present":
                if len(op["tags"]) == 0:
                    out.append({"pointer": op["pointer"], "path": loc, "message": "Operation is not assigned any tag"})
    return out
