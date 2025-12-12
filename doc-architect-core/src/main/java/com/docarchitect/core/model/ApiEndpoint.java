package com.docarchitect.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents an API endpoint (REST, GraphQL, gRPC, etc.).
 *
 * @param componentId component that exposes this endpoint
 * @param type API type
 * @param path endpoint path or operation name
 * @param method HTTP method (for REST) or operation type
 * @param description optional description
 * @param requestSchema request payload schema or type
 * @param responseSchema response payload schema or type
 * @param authentication authentication mechanism (if any)
 */
public record ApiEndpoint(
    String componentId,
    ApiType type,
    String path,
    String method,
    String description,
    String requestSchema,
    String responseSchema,
    String authentication
) {
    /**
     * Compact constructor with validation.
     */
    public ApiEndpoint {
        Objects.requireNonNull(componentId, "componentId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(path, "path must not be null");
    }
}
