package com.apiv.openapiviewer.service;

import io.swagger.v3.oas.models.OpenAPI;

import java.util.List;

public record ParsedSpec(OpenAPI openApi, List<String> messages) {
}
