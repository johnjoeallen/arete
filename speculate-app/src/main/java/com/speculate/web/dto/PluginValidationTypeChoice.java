package com.speculate.web.dto;

import java.util.List;

/**
 * One enabled plugin's set of named validation types, for the "add a spec"
 * forms to render as a picker. Only plugins that actually declare more than
 * one type appear here — a single-type plugin has no real choice to offer.
 */
public record PluginValidationTypeChoice(String pluginId, String pluginName, List<String> validationTypes) {
}
