_CONNECTORS = ["a", "an", "and", "as", "at", "but", "by", "for", "in", "of", "on", "or", "the", "to", "via", "with"]


def detect(api, rule):
    p = rule["parameters"]
    title = api["info"].get("title")
    out = []
    if type(title) != "string" or title.strip() == "":
        return out
    pointer = "/info/title"

    suffix = p.get("suffix")
    if suffix and not title.rstrip().endswith(suffix):
        out.append({"pointer": pointer, "path": title, "message": "API title does not end with '" + suffix + "'"})

    words = tokenize(title, " ")
    last_word = words[len(words) - 1].strip("()[]:,.").lower() if len(words) > 0 else ""
    for token in str(p.get("forbidden", "")).split(","):
        token = token.strip()
        if token and token.lower() == last_word:
            out.append({"pointer": pointer, "path": title, "message": "API title ends with the discouraged marker '" + token + "'"})

    if p.get("case") == "title-case":
        for word in tokenize(title, " "):
            core = word.strip("()[]:,.")
            if core == "" or core.lower() in _CONNECTORS or len(core) <= 3:
                continue
            if not re_fullmatch(r"[A-Z0-9].*", core):
                out.append({"pointer": pointer, "path": title, "message": "API title word '" + word + "' is not in Title Case"})
    return out
