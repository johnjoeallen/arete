def detect(api, rule):
    check = rule["parameters"]["check"]
    out = []

    if check == "tags-present":
        for path in api["paths"]:
            for op in path["operationDetails"]:
                if len(op["tags"]) == 0:
                    out.append({"pointer": op["pointer"],
                                "path": op["method"] + " " + path["path"],
                                "message": "Operation is not assigned any tag"})
        return out

    if check != "unique-operation-id":
        return out

    # Group operations by operationId, in first-seen order, then report the
    # blank ones and the second-and-later user of each duplicated id.
    groups = {}
    order = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            entry = (op["pointer"], op["method"] + " " + path["path"], op["operationId"])
            key = str(entry[2])
            if key not in groups:
                groups[key] = []
                order.append(key)
            groups[key].append(entry)

    for key in order:
        group = groups[key]
        head = group[0]
        if head[2] == None or (type(head[2]) == "string" and head[2].strip() == ""):
            for entry in group:
                out.append({"pointer": entry[0], "path": entry[1],
                            "message": "Operation has no operationId"})
        elif len(group) > 1:
            for entry in group[1:]:
                out.append({"pointer": entry[0], "path": entry[1],
                            "message": "operationId '" + str(head[2]) + "' is also used by " + head[1]})
    return out
