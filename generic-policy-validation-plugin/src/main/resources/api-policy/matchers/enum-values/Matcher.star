def detect(api, rule):
    check = rule["parameters"]["check"]
    out = []
    for schema in api["schemas"]:
        for prop in schema["properties"]:
            if not prop["enumPresent"]:
                continue
            if check == "no-duplicates":
                seen = []
                duplicate = False
                for value in prop["enumValues"]:
                    key = str(value)
                    if key in seen:
                        duplicate = True
                        break
                    seen.append(key)
                if duplicate:
                    out.append({
                        "pointer": prop["pointer"],
                        "path": prop["name"],
                        "message": "Enum for '" + prop["name"] + "' contains duplicate values",
                    })
    return out
