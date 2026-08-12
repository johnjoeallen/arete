package com.speculate.web.dto;

import java.util.List;

public record TagGroup(String name, String description, List<EndpointView> endpoints) {
}
