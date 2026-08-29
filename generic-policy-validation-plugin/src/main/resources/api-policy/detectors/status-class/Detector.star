def detect(api, rule):
    forbidden = rule["parameters"]["forbidden"]
    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            for resp in op["responses"]:
                code = parse_int(str(resp["status"]), -1)
                if forbidden == "server-error" and code >= 500 and code < 600:
                    out.append({
                        "pointer": op["pointer"],
                        "path": op["method"] + " " + path["path"] + " " + str(resp["status"]),
                        "message": "Documents a server-error (" + str(resp["status"]) + ") response; these should be omitted from the contract",
                    })
    return out
