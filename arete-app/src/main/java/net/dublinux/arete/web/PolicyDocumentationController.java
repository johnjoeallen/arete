package net.dublinux.arete.web;

import net.dublinux.arete.plugin.PluginRegistry;
import net.dublinux.arete.service.MarkdownRenderer;
import net.dublinux.arete.validation.spi.RuleDocumentation;
import net.dublinux.arete.validation.spi.RuleDocumentationProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** Serves rule documentation owned by a loaded plugin at a stable local URL. */
@Controller
public class PolicyDocumentationController {
    private final PluginRegistry pluginRegistry;
    private final MarkdownRenderer markdownRenderer;

    public PolicyDocumentationController(PluginRegistry pluginRegistry, MarkdownRenderer markdownRenderer) {
        this.pluginRegistry = pluginRegistry;
        this.markdownRenderer = markdownRenderer;
    }

    @GetMapping("/plugins/{pluginId}/rules/{ruleId}")
    public String rule(@PathVariable String pluginId, @PathVariable String ruleId, Model model) {
        RuleDocumentation documentation = pluginRegistry.getPlugins().stream()
                .filter(plugin -> plugin.getId().equals(pluginId))
                .filter(RuleDocumentationProvider.class::isInstance)
                .map(RuleDocumentationProvider.class::cast)
                .map(provider -> provider.getRuleDocumentation(ruleId))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("documentationTitle", documentation.title());
        model.addAttribute("renderedDocumentation", markdownRenderer.render(documentation.markdown()));
        return "rule-documentation";
    }
}
