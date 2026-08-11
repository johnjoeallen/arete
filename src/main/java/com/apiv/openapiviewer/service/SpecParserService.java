package com.apiv.openapiviewer.service;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.DeserializationUtils;
import org.springframework.stereotype.Service;

@Service
public class SpecParserService {

    static {
        // swagger-parser's SnakeYAML-backed loader caps documents at 3MB by
        // default (a YAML-bomb guard). Real-world specs (e.g. GitHub's REST
        // API) routinely exceed that, so raise it to match our form-post
        // size limit (see server.tomcat.max-http-form-post-size).
        DeserializationUtils.getOptions().setMaxYamlCodePoints(50 * 1024 * 1024);
    }

    public ParsedSpec parse(String rawSpec) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readContents(rawSpec, null, options);
        return new ParsedSpec(result.getOpenAPI(), result.getMessages());
    }

}
