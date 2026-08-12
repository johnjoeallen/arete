package com.speculate.service;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;

/**
 * Small null-safe helpers for rendering request/response body content in
 * Thymeleaf templates, where chained property access on a possibly-missing
 * value would otherwise throw.
 */
@Component
public class ContentUtils {

    public Schema<?> firstSchema(Content content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        for (MediaType mediaType : content.values()) {
            if (mediaType != null && mediaType.getSchema() != null) {
                return mediaType.getSchema();
            }
        }
        return null;
    }

}
