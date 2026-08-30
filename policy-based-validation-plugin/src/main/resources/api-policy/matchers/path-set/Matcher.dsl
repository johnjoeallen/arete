distill(api, rule) {
    return api.paths
        .filter { path -> api.paths.find { other ->
            join("/", tokenize("/", other.path).map { s -> (s.startsWith("{") && s.endsWith("}")) ? "{}" : s })
            == join("/", tokenize("/", path.path).map { s -> (s.startsWith("{") && s.endsWith("}")) ? "{}" : s }) }
            != path }
        .map { path -> diagnostic(path.pointer, path.path,
            "Path is structurally identical to "
            + api.paths.find { other ->
                join("/", tokenize("/", other.path).map { s -> (s.startsWith("{") && s.endsWith("}")) ? "{}" : s })
                == join("/", tokenize("/", path.path).map { s -> (s.startsWith("{") && s.endsWith("}")) ? "{}" : s }) }.path) };
}
