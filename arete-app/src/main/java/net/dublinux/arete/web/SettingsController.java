package net.dublinux.arete.web;

import net.dublinux.arete.service.NamespaceService;
import net.dublinux.arete.service.SpecStorageService;
import net.dublinux.arete.web.dto.SpecSummary;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;

@Controller
public class SettingsController {

    private final SpecStorageService specStorageService;
    private final NamespaceService namespaceService;

    public SettingsController(SpecStorageService specStorageService, NamespaceService namespaceService) {
        this.specStorageService = specStorageService;
        this.namespaceService = namespaceService;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("specs", specStorageService.findAll().stream()
                .map(e -> new SpecSummary(e.getRef(), e.getTitle(), e.getUpdatedAt().toEpochMilli()))
                .sorted(Comparator.comparing(SpecSummary::title, String.CASE_INSENSITIVE_ORDER))
                .toList());
        model.addAttribute("q", null);
        model.addAttribute("specId", null);
        model.addAttribute("namespaces", namespaceService.list());
        return "settings";
    }

    @PostMapping("/settings/namespaces")
    public String createNamespace(@RequestParam String name) {
        namespaceService.create(name);
        return "redirect:/settings#namespaces";
    }

    @PostMapping("/settings/namespaces/{key}/delete")
    public String deleteNamespace(@PathVariable String key) {
        namespaceService.deleteIfEmpty(key);
        return "redirect:/settings#namespaces";
    }

}
