package com.apiv.openapiviewer.web.dto;

import io.swagger.v3.oas.models.Operation;

public record EndpointView(String path, String method, Operation operation) {
}
