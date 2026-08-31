package net.dublinux.arete.web;

import net.dublinux.arete.plugin.PluginRegistry;
import net.dublinux.arete.plugin.PluginSettingsService;
import net.dublinux.arete.service.NamespaceService;
import net.dublinux.arete.service.SpecStorageService;
import net.dublinux.arete.web.dto.PluginSettingRow;
import net.dublinux.arete.web.dto.SpecSummary;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;

@Controller
public class SettingsController {

    private final PluginRegistry pluginRegistry;
    private final PluginSettingsService pluginSettingsService;
    private final SpecStorageService specStorageService;
    private final NamespaceService namespaceService;

    public SettingsController(PluginRegistry pluginRegistry, PluginSettingsService pluginSettingsService,
            SpecStorageService specStorageService, NamespaceService namespaceService) {
        this.pluginRegistry = pluginRegistry;
        this.pluginSettingsService = pluginSettingsService;
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
        model.addAttribute("pluginRows", pluginRows());
        model.addAttribute("namespaces", namespaceService.list());
        model.addAttribute("installPluginsDir", pluginRegistry.getInstallPluginsDir().toString());
        model.addAttribute("userPluginsDir", pluginRegistry.getUserPluginsDir().toString());
        return "settings";
    }

    @PostMapping("/settings/plugins/{id}/toggle")
    public String togglePlugin(@PathVariable String id) {
        pluginSettingsService.setEnabled(id, !pluginSettingsService.isEnabled(id));
        return "redirect:/settings";
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

    private List<PluginSettingRow> pluginRows() {
        return pluginRegistry.getPlugins().stream()
                .map(p -> new PluginSettingRow(p.getId(), p.getName(), pluginSettingsService.isEnabled(p.getId())))
                .sorted(Comparator.comparing(PluginSettingRow::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

}
