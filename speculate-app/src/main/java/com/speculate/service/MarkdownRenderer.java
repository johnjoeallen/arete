package com.speculate.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

/**
 * Renders OpenAPI {@code description} fields as HTML. The spec allows
 * CommonMark in these fields (see the "description" field docs at
 * https://spec.openapis.org/oas/v3.1.0#schema-object), and raw HTML mixed
 * into that Markdown is passed through rather than escaped, matching how
 * Swagger Editor renders descriptions. The rendered HTML is run through an
 * allowlist sanitizer first, since spec content may originate from a
 * pasted third-party file: script tags, event handler attributes,
 * javascript: URLs, iframes, and similar are stripped, while ordinary
 * formatting (bold/italic, links, lists, tables, images, code blocks)
 * passes through.
 */
@Component
public class MarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    private final PolicyFactory sanitizerPolicy = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.IMAGES)
            .and(new HtmlPolicyBuilder().allowElements("pre", "hr").toFactory());

    public String render(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        Node document = parser.parse(source);
        String html = renderer.render(document);
        return sanitizerPolicy.sanitize(html);
    }

}
