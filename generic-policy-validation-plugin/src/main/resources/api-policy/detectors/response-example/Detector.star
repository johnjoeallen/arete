def detect(api, rule):
    check = rule["parameters"]["check"]
    out = []
    if check != "unique-error-payloads":
        return out

    for path in api["paths"]:
        for op in path["operationDetails"]:
            # (status, example) for every 4xx/5xx response body, in document order.
            pairs = []
            for resp in op["responses"]:
                code = parse_int(str(resp["status"]), -1)
                if code < 400 or code >= 600:
                    continue
                for example in resp.get("exampleStrings", []):
                    pairs.append((str(resp["status"]), example))

            groups = {}
            order = []
            for pair in pairs:
                if pair[1] not in groups:
                    groups[pair[1]] = []
                    order.append(pair[1])
                groups[pair[1]].append(pair)

            for key in order:
                group = groups[key]
                for pair in group[1:]:
                    out.append({
                        "pointer": op["pointer"],
                        "path": op["method"] + " " + path["path"],
                        "message": "Error responses " + group[0][0] + " and " + pair[0]
                                   + " share an identical example payload",
                    })
    return out
