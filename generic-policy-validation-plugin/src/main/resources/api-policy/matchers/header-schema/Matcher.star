def detect(api, rule):
    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            for resp in op["responses"]:
                for header in resp["headerDetails"]:
                    if not header["schemaPresent"]:
                        out.append({
                            "pointer": op["pointer"],
                            "path": op["method"] + " " + path["path"] + " " + str(resp["status"]),
                            "message": "Response header '" + header["name"] + "' defines neither a schema nor content",
                        })
    return out
