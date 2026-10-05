package net.dublinux.arete.web;

import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.service.MarkdownRenderer;
import net.dublinux.arete.engine.api.RuleDocumentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** Serves rule documentation owned by a loaded engine at a stable local URL. */
@Controller
public class PolicyDocumentationController {
    private final Engine engine;
    private final MarkdownRenderer markdownRenderer;

    public PolicyDocumentationController(Engine engine, MarkdownRenderer markdownRenderer) {
        this.engine = engine;
        this.markdownRenderer = markdownRenderer;
    }

    @GetMapping("/engines/{engineId}/rules/{ruleId}")
    public String rule(@PathVariable String engineId, @PathVariable String ruleId, Model model) {
        RuleDocumentation documentation = Optional.of(engine)
                .filter(candidate -> candidate.getId().equals(engineId))
                .flatMap(candidate -> candidate.getRuleDocumentation(ruleId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("documentationTitle", documentation.title());
        model.addAttribute("renderedDocumentation", markdownRenderer.render(documentation.markdown()));
        return "rule-documentation";
    }
}
