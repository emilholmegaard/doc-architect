package com.docarchitect.core.model;

import java.util.Map;
import java.util.Objects;

/**
 * Represents an architectural component (service, module, library, etc.).
 *
 * @param id unique identifier (generated via IdGenerator)
 * @param name component name
 * @param type component type
 * @param description optional description
 * @param technology primary technology or framework
 * @param repository repository name or path
 * @param metadata additional metadata (version, tags, etc.)
 */
public record Component(
    String id,
    String name,
    ComponentType type,
    String description,
    String technology,
    String repository,
    Map<String, String> metadata
) {
    /**
     * Compact constructor with validation.
     */
    public Component {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
