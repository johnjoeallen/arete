package com.speculate.web;

import com.speculate.plugin.PluginRegistry;
import com.speculate.plugin.PluginSettingsService;
import com.speculate.service.SpecStorageService;
import com.speculate.web.dto.PluginSettingRow;
import com.speculate.web.dto.SpecSummary;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Comparator;
import java.util.List;

@Controller
public class SettingsController {

    private final PluginRegistry pluginRegistry;
    private final PluginSettingsService pluginSettingsService;
    private final SpecStorageService specStorageService;

    public SettingsController(PluginRegistry pluginRegistry, PluginSettingsService pluginSettingsService,
            SpecStorageService specStorageService) {
        this.pluginRegistry = pluginRegistry;
        this.pluginSettingsService = pluginSettingsService;
        this.specStorageService = specStorageService;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("specs", specStorageService.findAll().stream()
                .map(e -> new SpecSummary(e.getId(), e.getTitle()))
                .sorted(Comparator.comparing(SpecSummary::title, String.CASE_INSENSITIVE_ORDER))
                .toList());
        model.addAttribute("q", null);
        model.addAttribute("specId", null);
        model.addAttribute("pluginRows", pluginRows());
        model.addAttribute("installPluginsDir", pluginRegistry.getInstallPluginsDir().toString());
        model.addAttribute("userPluginsDir", pluginRegistry.getUserPluginsDir().toString());
        return "settings";
    }

    @PostMapping("/settings/plugins/{id}/toggle")
    public String togglePlugin(@PathVariable String id) {
        pluginSettingsService.setEnabled(id, !pluginSettingsService.isEnabled(id));
        return "redirect:/settings";
    }

    private List<PluginSettingRow> pluginRows() {
        return pluginRegistry.getPlugins().stream()
                .map(p -> new PluginSettingRow(p.getId(), p.getName(), pluginSettingsService.isEnabled(p.getId())))
                .sorted(Comparator.comparing(PluginSettingRow::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

}
