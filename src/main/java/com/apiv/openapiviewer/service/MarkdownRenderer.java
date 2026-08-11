package com.apiv.openapiviewer.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

/**
 * Renders OpenAPI {@code description} fields as HTML. The spec allows
 * CommonMark in these fields (see the "description" field docs at
 * https://spec.openapis.org/oas/v3.1.0#schema-object). Raw HTML in the
 * source is escaped rather than passed through, since spec content may
 * originate from a pasted third-party file.
 */
@Component
public class MarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).build();

    public String render(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        Node document = parser.parse(source);
        return renderer.render(document);
    }

}
