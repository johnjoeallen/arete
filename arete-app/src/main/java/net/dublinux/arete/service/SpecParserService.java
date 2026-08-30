package net.dublinux.arete.service;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.DeserializationUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

@Service
public class SpecParserService {

    @Value("${arete.openapi.max-document-size:50MB}")
    private DataSize maxDocumentSize;

    @PostConstruct
    void configureYamlDocumentLimit() {
        // swagger-parser's SnakeYAML-backed loader caps documents at 3 MiB by
        // default (a YAML-bomb guard). Keep this aligned with the configured
        // application limit so trusted, large specifications can be loaded.
        long bytes = maxDocumentSize.toBytes();
        if (bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("arete.openapi.max-document-size must not exceed 2 GiB");
        }
        DeserializationUtils.getOptions().setMaxYamlCodePoints((int) bytes);
    }

    public ParsedSpec parse(String rawSpec) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readContents(rawSpec, null, options);
        return new ParsedSpec(result.getOpenAPI(), result.getMessages());
    }

}
