/*
 * Naming detector
 * ===============
 *
 * This script works only with the stable map model exported by
 * OpenApiMapAdapter. It never receives a Swagger `Schema`, `Parameter`, or
 * `PathItem`; detector bundles consequently remain independent of the host's
 * OpenAPI parser implementation.
 *
 * Input contract
 * --------------
 *
 * api.paths[*].segments[*]                  -> name, pointer
 * api.paths[*].operationDetails[*].parameters[*] -> name, in, pointer
 * api.schemas[*]                            -> name, pointer, properties
 * api.schemas[*].properties[*]              -> name, type, pointer
 *
 * Scope selects the candidates:
 *
 * property, schema, path-segment, path-parameter, query-parameter, header.
 * Path, query and header parameters are declarations from both the path item
 * and individual operation; keeping the occurrences separate is deliberate,
 * because a consumer must correct the declaration at its real location.
 *
 * Configuration semantics
 * -----------------------
 *
 * convention + match: non-conforming
 *     Reports names outside a declared casing grammar.
 * match: unsupported-character
 *     Reports a name outside the conservative portable-name grammar.
 * suffix + match: present
 *     Reports schema names ending with the configured suffix.
 * semantic: collection|singular|plural
 *     Uses the transparent English heuristic "ends in s". This deliberately
 *     does not guess irregular nouns: a richer inflection dictionary, if ever
 *     wanted, should be an explicit host or detector capability.
 * schema-type: array + semantic: singular
 *     Reports an array property whose name is singular. The check applies only
 *     when the property itself is a concrete OpenAPI array; `$ref` resolution
 *     is intentionally a future stable-model capability.
 *
 * All supplied conditions are ANDed. The descriptor validates names and value
 * types before execution. This script still handles no candidates safely and
 * returns an empty list, never a score.
 */
{ Map api, Map rule ->
    def parameters = rule.parameters ?: [:]

    def candidates = {
        switch (rule.scope) {
            case 'property': return api.schemas.collectMany { schema -> schema.properties ?: [] }
            case 'schema': return api.schemas ?: []
            case 'path-segment': return api.paths.collectMany { path -> path.segments ?: [] }
            case 'path-parameter': return api.paths.collectMany { path -> path.operationDetails.collectMany { operation -> operation.parameters.findAll { it.in == 'path' } } }
            case 'query-parameter': return api.paths.collectMany { path -> path.operationDetails.collectMany { operation -> operation.parameters.findAll { it.in == 'query' } } }
            case 'header': return api.paths.collectMany { path -> path.operationDetails.collectMany { operation -> operation.parameters.findAll { it.in == 'header' } } }
            default: return []
        }
    }

    def conforms = { name, convention ->
        switch (convention) {
            case 'camelCase': return name ==~ /[a-z][A-Za-z0-9]*/
            case 'snake_case': return name ==~ /[a-z][a-z0-9]*(?:_[a-z0-9]+)*/
            case 'kebab-case': return name ==~ /[a-z][a-z0-9]*(?:-[a-z0-9]+)*/
            // Custom header names should contain a separator where they have
            // more than one word; the policy's conventional-hyphenated rule
            // intentionally treats an unseparated mixed-case name as invalid.
            case 'hyphenated': return name ==~ /[A-Za-z0-9]+(?:-[A-Za-z0-9]+)+/
            default: return false
        }
    }
    def plural = { name -> name.toLowerCase().endsWith('s') && name.length() > 1 }
    def matches = { candidate ->
        def name = candidate.name
        if (parameters.convention && parameters.match == 'non-conforming' && conforms(name, parameters.convention)) return false
        if (parameters.match == 'unsupported-character' && name ==~ /[A-Za-z][A-Za-z0-9_-]*/) return false
        if (parameters.suffix && parameters.match == 'present' && !name.endsWith(parameters.suffix)) return false
        // `collection` identifies the resource context: report its name only
        // when it is singular. `singular` and `plural` identify the undesirable
        // name form directly for property-oriented rules.
        if (parameters.semantic == 'collection' && plural(name)) return false
        if (parameters.semantic == 'singular' && plural(name)) return false
        if (parameters.semantic == 'plural' && !plural(name)) return false
        if (parameters['schema-type'] == 'array' && candidate.type != 'array') return false
        true
    }
    def message = parameters.suffix ? 'Name has prohibited suffix ' + parameters.suffix
        : parameters.semantic == 'collection' ? 'Collection name is singular'
        : parameters.semantic == 'singular' ? 'Array property has a singular name'
        : parameters.match == 'unsupported-character' ? 'Name contains unsupported characters'
        : 'Name does not use the configured convention'

    candidates().findAll(matches).collect { candidate ->
        [pointer: candidate.pointer, path: candidate.name, message: message]
    }
}
